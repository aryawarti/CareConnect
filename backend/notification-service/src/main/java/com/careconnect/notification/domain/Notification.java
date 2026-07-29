package com.careconnect.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "recipient_ref", nullable = false)
    private String recipientRef;

    @Column(nullable = false)
    private String channel = "EMAIL";

    @Column(name = "template_code", nullable = false)
    private String templateCode;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String body;

    @Column(nullable = false)
    private String status = "SENT";

    @Column(name = "source_event_id", nullable = false, unique = true)
    private String sourceEventId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    protected Notification() { }

    public Notification(String recipientRef, String templateCode, String subject,
                        String body, String sourceEventId) {
        this.recipientRef = recipientRef;
        this.templateCode = templateCode;
        this.subject = subject;
        this.body = body;
        this.sourceEventId = sourceEventId;
    }

    public UUID getId() { return id; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getTemplateCode() { return templateCode; }
    public String getRecipientRef() { return recipientRef; }
}
