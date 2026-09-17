package com.messaging.billing.entity;

import com.messaging.billing.enums.BillingCategory;
import com.messaging.billing.enums.PricingDirection;
import com.messaging.billing.enums.RateCardStatus;
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
import jakarta.persistence.PreUpdate;
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
    name = "company_rate_overrides",
    indexes = {
      @Index(name = "idx_company_rate_overrides_lookup", columnList = "company_id,provider,billing_category,status"),
      @Index(name = "idx_company_rate_overrides_effective", columnList = "effective_from,effective_to")
    })
public class CompanyRateOverride {

  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ChannelType channel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 80)
  private ProviderType provider;

  @Enumerated(EnumType.STRING)
  @Column(name = "billing_category", nullable = false, length = 48)
  private BillingCategory billingCategory;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PricingDirection direction;

  @Column(length = 2)
  private String country;

  @Column(length = 80)
  private String carrier;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private UsageUnit unit;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal price;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  @Column(name = "effective_to")
  private Instant effectiveTo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private RateCardStatus status;

  @Column(name = "created_by_user_id")
  private Long createdByUserId;

  @Column(length = 500)
  private String reason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  private void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (effectiveFrom == null) effectiveFrom = now;
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  private void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
