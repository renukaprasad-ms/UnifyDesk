package com.messaging.delivery.entity;

import com.messaging.broadcast.enums.BroadcastBounceType;
import com.messaging.common.id.IdGenerator;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "email_delivery_reports")
public class EmailDeliveryReport {
  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "connection_id", nullable = false)
  private Long connectionId;

  @Column(name = "source_inbox_message_id", nullable = false, unique = true)
  private Long sourceInboxMessageId;

  @Column(name = "failed_recipient", length = 320)
  private String failedRecipient;

  @Column(name = "normalized_failed_recipient", length = 320)
  private String normalizedFailedRecipient;

  @Column(name = "original_message_id", length = 998)
  private String originalMessageId;

  @Column(name = "status_code", length = 32)
  private String statusCode;

  @Column(name = "diagnostic_message", length = 1000)
  private String diagnosticMessage;

  @Enumerated(EnumType.STRING)
  @Column(name = "bounce_type", nullable = false, length = 24)
  private BroadcastBounceType bounceType;

  @Column(name = "detected_at", nullable = false)
  private Instant detectedAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (detectedAt == null) detectedAt = Instant.now();
  }
}
