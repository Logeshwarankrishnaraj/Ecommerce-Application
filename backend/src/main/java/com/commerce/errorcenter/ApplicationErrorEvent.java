package com.commerce.errorcenter;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "application_error_events")
public class ApplicationErrorEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;
    private String signature;
    private String correlationId;
    private String severity;
    private Instant occurredAt;
    private Instant lastOccurredAt;
    private int occurrenceCount = 1;
    private String operation;
    private String route;
    private String errorType;
    private String summary;
    private String environment;
    private String notificationState = "NONE";
    private String remediationState = "OPEN";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public Instant getLastOccurredAt() { return lastOccurredAt; }
    public void setLastOccurredAt(Instant lastOccurredAt) { this.lastOccurredAt = lastOccurredAt; }
    public int getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(int occurrenceCount) { this.occurrenceCount = occurrenceCount; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getNotificationState() { return notificationState; }
    public void setNotificationState(String notificationState) { this.notificationState = notificationState; }
    public String getRemediationState() { return remediationState; }
    public void setRemediationState(String remediationState) { this.remediationState = remediationState; }
}
