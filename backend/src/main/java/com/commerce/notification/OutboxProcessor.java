package com.commerce.notification;

import com.commerce.errorcenter.ApplicationErrorEvent;
import com.commerce.errorcenter.ApplicationErrorEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Sends queued mail outside any business transaction, with bounded exponential-backoff retries. */
@Component
public class OutboxProcessor {
    static final int MAX_ATTEMPTS = 5;
    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final EmailOutboxRepository repo;
    private final ApplicationErrorEventRepository errors;
    private final MailService mail;
    private final TransactionTemplate tx;
    private final Clock clock;

    public OutboxProcessor(EmailOutboxRepository repo, ApplicationErrorEventRepository errors, MailService mail,
                           TransactionTemplate tx, Clock clock) {
        this.repo = repo;
        this.errors = errors;
        this.mail = mail;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:15000}")
    public void processDue() {
        List<EmailOutbox> due = repo.findDue(clock.instant(), PageRequest.of(0, 20));
        for (EmailOutbox o : due) {
            deliver(o.getId());
        }
    }

    void deliver(Long id) {
        EmailOutbox o = repo.findById(id).orElse(null);
        if (o == null || !"PENDING".equals(o.getState())) return;
        String failure = null;
        try {
            mail.send(o.getRecipient(), o.getSubject(), o.getBody());
        } catch (RuntimeException ex) {
            failure = ex.getClass().getSimpleName();
        }
        final String err = failure;
        tx.executeWithoutResult(s -> {
            EmailOutbox row = repo.findById(id).orElseThrow();
            Instant now = clock.instant();
            row.setAttempts(row.getAttempts() + 1);
            row.setUpdatedAt(now);
            if (err == null) {
                row.setState("SENT");
                row.setErrorSummary(null);
            } else if (row.getAttempts() >= MAX_ATTEMPTS) {
                row.setState("FAILED");
                row.setErrorSummary(err);
            } else {
                row.setErrorSummary(err);
                row.setNextAttemptAt(now.plus(Duration.ofSeconds(30L * (1L << row.getAttempts()))));
            }
            if ("ERROR_EVENT".equals(row.getRefType()) && row.getRefId() != null && !"PENDING".equals(row.getState())) {
                ApplicationErrorEvent ev = errors.findById(row.getRefId()).orElse(null);
                if (ev != null) ev.setNotificationState("SENT".equals(row.getState()) ? "SENT" : "FAILED");
            }
        });
        if (err != null) log.warn("Outbox message {} delivery attempt failed ({})", id, err);
    }
}
