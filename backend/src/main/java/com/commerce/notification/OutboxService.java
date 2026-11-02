package com.commerce.notification;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {
    private final EmailOutboxRepository repo;
    private final Clock clock;

    public OutboxService(EmailOutboxRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Transactional
    public EmailOutbox enqueue(String type, String to, String subject, String body, String refType, Long refId) {
        Instant now = clock.instant();
        EmailOutbox o = new EmailOutbox();
        o.setMessageType(type);
        o.setRecipient(to);
        o.setSubject(subject);
        o.setBody(body);
        o.setRefType(refType);
        o.setRefId(refId);
        o.setNextAttemptAt(now);
        o.setCreatedAt(now);
        o.setUpdatedAt(now);
        return repo.save(o);
    }
}
