package com.messaging.billing.entity;

import com.messaging.billing.enums.BillingCategory;
import com.messaging.billing.enums.PricingDirection;
import com.messaging.billing.enums.PricingSource;
import com.messaging.billing.enums.UsageUnit;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
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
import jakarta.persistence.UniqueConstraint;
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
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_usage_events_idempotency_key", columnNames = "idempotency_key")
    },
    indexes = {
      @Index(name = "idx_usage_events_company_time", columnList = "company_id,occurred_at"),
      @Index(name = "idx_usage_events_channel", columnList = "channel"),
      @Index(name = "idx_usage_events_source", columnList = "source_type,source_id")
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

  @Column(name = "pricing_rule_id")
  private Long pricingRuleIdSnapshot;

  @Enumerated(EnumType.STRING)
  @Column(name = "pricing_source", length = 32)
  private PricingSource pricingSource;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ChannelType channel;

  @Enumerated(EnumType.STRING)
  @Column(length = 80)
  private ProviderType provider;

  @Column(length = 2)
  private String country;

  @Column(length = 80)
  private String carrier;

  @Enumerated(EnumType.STRING)
  @Column(name = "billing_category", nullable = false, length = 48)
  private BillingCategory billingCategory;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PricingDirection direction;

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

  @Column(name = "source_type", length = 48)
  private String sourceType;

  @Column(name = "source_id", length = 120)
  private String sourceId;

  @Column(name = "idempotency_key", nullable = false, length = 200)
  private String idempotencyKey;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  private void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (occurredAt == null) occurredAt = now;
    if (createdAt == null) createdAt = now;
  }
}
