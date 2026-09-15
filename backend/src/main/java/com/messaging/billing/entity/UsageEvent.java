package com.messaging.billing.entity;

import com.messaging.billing.enums.ChannelType;
import com.messaging.billing.enums.CommunicationDirection;
import com.messaging.billing.enums.MessageType;
import com.messaging.billing.enums.UsageUnit;
import com.messaging.common.id.IdGenerator;
import com.messaging.company.entity.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "usage_events",
    indexes = {
      @Index(name = "idx_usage_events_company_time", columnList = "company_id,occurred_at"),
      @Index(name = "idx_usage_events_channel", columnList = "channel")
    })
public class UsageEvent {

  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @Column(name = "plan_id")
  private Long planIdSnapshot;

  @Column(name = "rate_card_id")
  private Long rateCardIdSnapshot;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ChannelType channel;

  @Column(length = 80)
  private String provider;

  @Column(length = 2)
  private String country;

  @Column(length = 80)
  private String carrier;

  @Enumerated(EnumType.STRING)
  @Column(name = "message_type", nullable = false, length = 32)
  private MessageType messageType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private CommunicationDirection direction;

  @Column(nullable = false)
  private long quantity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private UsageUnit unit;

  @Column(name = "unit_price_snapshot", nullable = false, precision = 19, scale = 6)
  private BigDecimal unitPriceSnapshot;

  @Column(name = "total_price", nullable = false, precision = 19, scale = 6)
  private BigDecimal totalPrice;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  private void beforeInsert() {
    if (id == null) {
      id = IdGenerator.nextId();
    }
    Instant now = Instant.now();
    if (occurredAt == null) {
      occurredAt = now;
    }
    if (createdAt == null) {
      createdAt = now;
    }
  }
}
