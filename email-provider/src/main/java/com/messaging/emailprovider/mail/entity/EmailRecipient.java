package com.messaging.emailprovider.mail.entity;

import com.messaging.emailprovider.common.persistence.BaseEntity;
import com.messaging.emailprovider.mail.enums.EmailRecipientStatus;
import com.messaging.emailprovider.mail.enums.EmailRecipientType;
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
    name = "email_recipients",
    indexes = {
      @Index(name = "idx_email_recipients_message_id", columnList = "message_id"),
      @Index(name = "idx_email_recipients_address", columnList = "recipient_address"),
      @Index(name = "idx_email_recipients_status", columnList = "status"),
      @Index(name = "idx_email_recipients_postfix_queue_id", columnList = "postfix_queue_id")
    })
public class EmailRecipient extends BaseEntity {

  @Column(name = "message_id", nullable = false)
  private Long messageId;

  @Column(name = "recipient_address", nullable = false, length = 320)
  private String recipientAddress;

  @Enumerated(EnumType.STRING)
  @Column(name = "recipient_type", nullable = false, length = 16)
  private EmailRecipientType recipientType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private EmailRecipientStatus status = EmailRecipientStatus.QUEUED;

  @Column(name = "smtp_status_code", length = 16)
  private String smtpStatusCode;

  @Column(name = "smtp_response", length = 2048)
  private String smtpResponse;

  @Column(name = "postfix_queue_id", length = 128)
  private String postfixQueueId;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "last_attempt_at")
  private Instant lastAttemptAt;

  @Column(name = "handed_off_at")
  private Instant handedOffAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;
}
