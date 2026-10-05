package com.messaging.billing.entity;

import com.messaging.billing.enums.BillingCategory;
import com.messaging.billing.enums.CommunicationDirection;
import com.messaging.billing.enums.RateCardStatus;
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
    name = "company_rate_overrides",
    indexes = {
      @Index(name = "idx_company_rate_overrides_company_status", columnList = "company_id,status"),
      @Index(
          name = "idx_company_rate_overrides_effective",
          columnList = "effective_from,effective_to")
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
  @Column(name = "billing_category", nullable = false, length = 64)
  private BillingCategory billingCategory;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private CommunicationDirection direction;

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

  @Column(name = "supersedes_override_id")
  private Long supersedesOverrideId;

  @Column(name = "superseded_at")
  private Instant supersededAt;

  @Column(name = "retention_until", nullable = false, insertable = false, updatable = false)
  private Instant retentionUntil;

  @Column(name = "legal_hold", nullable = false)
  private boolean legalHold;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
