package com.messaging.billing.entity;

import com.messaging.billing.enums.BillingCategory;
import com.messaging.billing.enums.BillingSku;
import com.messaging.billing.enums.CommunicationDirection;
import com.messaging.billing.enums.PricingSource;
import com.messaging.billing.enums.ServiceOwnership;
import com.messaging.billing.enums.UsageUnit;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.common.id.IdGenerator;
import com.messaging.company.entity.Company;
import jakarta.persistence.*;
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
    uniqueConstraints =
        @UniqueConstraint(name = "uk_usage_events_idempotency", columnNames = "idempotency_key"),
    indexes = {
      @Index(name = "idx_usage_events_company_time", columnList = "company_id,occurred_at"),
      @Index(name = "idx_usage_events_channel", columnList = "channel"),
      @Index(
          name = "idx_usage_events_financial_period",
          columnList = "company_id,service_ownership,billing_period_id,occurred_at")
    })
public class UsageEvent {

  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @Column(name = "plan_id")
  private Long planIdSnapshot;

  @Column(name = "subscription_id_snapshot")
  private Long subscriptionIdSnapshot;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "billing_period_id")
  private BillingPeriod billingPeriod;

  @Enumerated(EnumType.STRING)
  @Column(name = "service_ownership", nullable = false, length = 32)
  private ServiceOwnership serviceOwnership;

  @Enumerated(EnumType.STRING)
  @Column(name = "billing_sku", length = 64)
  private BillingSku billingSku;

  @Column(name = "rate_card_id")
  private Long rateCardIdSnapshot;

  @Column(name = "pricing_rule_id")
  private Long pricingRuleIdSnapshot;

  @Enumerated(EnumType.STRING)
  @Column(name = "pricing_source", length = 32)
  private PricingSource pricingSource;

  @Column(name = "source_type", nullable = false, length = 64)
  private String sourceType;

  @Column(name = "source_id", nullable = false, length = 160)
  private String sourceId;

  @Column(name = "provider_reference", length = 255)
  private String providerReference;

  @Column(name = "idempotency_key", nullable = false, length = 255)
  private String idempotencyKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ChannelType channel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 80)
  private ProviderType provider;

  @Column(length = 2)
  private String country;

  @Column(length = 80)
  private String carrier;

  @Enumerated(EnumType.STRING)
  @Column(name = "message_type", nullable = false, length = 64)
  private BillingCategory billingCategory;

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
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (occurredAt == null) occurredAt = now;
    if (createdAt == null) createdAt = now;
  }
}
