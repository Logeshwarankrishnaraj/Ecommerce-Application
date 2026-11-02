package com.commerce.errorcenter;

import com.commerce.common.util.Hashing;
import com.commerce.config.AppProperties;
import com.commerce.notification.OutboxService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists sanitized unexpected-error events and queues admin alerts (SRS section 8).
 * Runs in its own transaction so it never alters the outcome of the failed business transaction.
 */
@Service
public class ErrorEventService {
    private static final Pattern SECRET_KV = Pattern.compile("(?i)(password|passwd|otp|token|secret|authorization|cookie|code)\\s*[=:]\\s*\\S+");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d{6,}");

    private final ApplicationErrorEventRepository repo;
    private final OutboxService outbox;
    private final AppProperties props;
    private final Environment env;
    private final Clock clock;

    public ErrorEventService(ApplicationErrorEventRepository repo, OutboxService outbox, AppProperties props, Environment env, Clock clock) {
        this.repo = repo;
        this.outbox = outbox;
        this.props = props;
        this.env = env;
        this.clock = clock;
    }

    /** Removes secrets / personal data and bounds length. Public for unit tests. */
    public static String sanitize(String raw) {
        if (raw == null) return "";
        String s = SECRET_KV.matcher(raw).replaceAll("$1=[REDACTED]");
        s = EMAIL.matcher(s).replaceAll("[email]");
        s = LONG_DIGITS.matcher(s).replaceAll("[num]");
        s = s.replaceAll("[\\r\\n]+", " ");
        return s.length() > 300 ? s.substring(0, 300) : s;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ApplicationErrorEvent record(Throwable t, String operation, String route, String correlationId, String severity) {
        Instant now = clock.instant();
        String type = t.getClass().getName();
        String sig = Hashing.sha256Hex(type + "|" + operation);
        Instant since = now.minus(props.getErrors().getDedupeWindowMinutes(), ChronoUnit.MINUTES);
        List<ApplicationErrorEvent> recent = repo.findRecentBySignature(sig, since, PageRequest.of(0, 1));
        if (!recent.isEmpty()) {
            ApplicationErrorEvent ev = recent.get(0);
            ev.setOccurrenceCount(ev.getOccurrenceCount() + 1);
            ev.setLastOccurredAt(now);
            return ev; // throttled: no extra alert (SRS ERR-04)
        }
        ApplicationErrorEvent ev = new ApplicationErrorEvent();
        ev.setSignature(sig);
        ev.setCorrelationId(correlationId);
        ev.setSeverity(severity);
        ev.setOccurredAt(now);
        ev.setLastOccurredAt(now);
        ev.setOperation(operation);
        ev.setRoute(sanitize(route));
        ev.setErrorType(type.length() > 200 ? type.substring(0, 200) : type);
        ev.setSummary(sanitize(t.getMessage() == null ? type : t.getMessage()));
        ev.setEnvironment(envName());
        List<String> recipients = props.getMail().adminAlertList();
        ev.setNotificationState(recipients.isEmpty() ? "NONE" : "PENDING");
        ev = repo.save(ev);
        String subject = "[Jewelry Store][" + ev.getEnvironment() + "][" + severity + "] Application error " + correlationId;
        String body = "Time: " + now + "\nEnvironment: " + ev.getEnvironment() + "\nSeverity: " + severity
                + "\nCorrelation ID: " + correlationId + "\nOperation: " + operation
                + "\nError type: " + ev.getErrorType() + "\nSummary: " + ev.getSummary()
                + "\n\nOpen the admin Error Center for details: " + props.getPublicBaseUrl() + "/admin/errors";
        for (String to : recipients) {
            outbox.enqueue("ADMIN_ERROR_ALERT", to, subject, body, "ERROR_EVENT", ev.getId());
        }
        return ev;
    }

    private String envName() {
        String[] p = env.getActiveProfiles();
        return p.length == 0 ? "prod" : String.join(",", p);
    }
}
