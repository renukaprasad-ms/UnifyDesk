package com.messaging.emailprovider.delivery;

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
    name = "email_delivery_attempts",
    indexes = {
      @Index(
          name = "idx_email_delivery_attempt_recipient",
          columnList = "recipient_id,attempt_number")
    })
public class EmailDeliveryAttempt extends BaseEntity {

  @Column(name = "recipient_id", nullable = false)
  private Long recipientId;

  @Column(name = "attempt_number", nullable = false)
  private int attemptNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private DeliveryAttemptOutcome outcome;

  @Column(name = "smtp_status_code", length = 16)
  private String smtpStatusCode;

  @Column(name = "smtp_response", length = 2048)
  private String smtpResponse;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "completed_at", nullable = false)
  private Instant completedAt;
}
