package com.messaging.emailprovider.feedback;

import com.messaging.emailprovider.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "email_events",
    indexes = {
      @Index(name = "idx_email_events_message", columnList = "message_id,occurred_at"),
      @Index(name = "idx_email_events_recipient", columnList = "recipient_id,occurred_at")
    })
public class EmailEvent extends BaseEntity {

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "message_id", nullable = false)
  private Long messageId;

  @Column(name = "recipient_id", nullable = false)
  private Long recipientId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 32)
  private EmailEventType eventType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private EmailEventSource source;

  @Column(name = "external_event_id", length = 256)
  private String externalEventId;

  @Column(name = "smtp_status_code", length = 32)
  private String smtpStatusCode;

  @Column(length = 2048)
  private String diagnostic;

  @Column(name = "raw_payload", columnDefinition = "TEXT")
  private String rawPayload;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;
}
