package com.messaging.emailprovider.queue;

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
    name = "email_outbox",
    indexes = {
      @Index(
          name = "idx_email_outbox_dispatch",
          columnList = "status,next_attempt_at,created_at")
    })
public class EmailOutbox extends BaseEntity {

  @Column(nullable = false, length = 200)
  private String topic;

  @Column(name = "event_key", nullable = false, length = 200)
  private String eventKey;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private OutboxStatus status = OutboxStatus.PENDING;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "claimed_at")
  private Instant claimedAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "last_error", length = 2048)
  private String lastError;
}
