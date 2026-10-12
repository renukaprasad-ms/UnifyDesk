package com.messaging.emailprovider.product.webhook;

import com.messaging.emailprovider.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @Entity
@Table(name = "email_webhook_deliveries")
public class WebhookDelivery extends BaseEntity {
  @Column(name = "endpoint_id", nullable = false)
  private Long endpointId;
  @Column(name = "event_id", nullable = false)
  private Long eventId;
  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;
  @Column(nullable = false, length = 16)
  private String status = "PENDING";
  @Column(nullable = false)
  private int attempts;
  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt = Instant.now();
  @Column(name = "claimed_at")
  private Instant claimedAt;
  @Column(name = "delivered_at")
  private Instant deliveredAt;
  @Column(name = "last_error", length = 256)
  private String lastError;
}
