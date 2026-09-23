package com.messaging.ads.entity;

import com.messaging.ads.enums.AdProvider;
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
@Table(
    name = "ad_account_billing",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_ad_account_billing",
          columnNames = {"ad_account_id", "provider"})
    },
    indexes = {
      @Index(
          name = "idx_ad_account_billing_company",
          columnList = "company_id,provider")
    })
public class AdAccountBilling {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "ad_account_id", nullable = false)
  private Long adAccountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdProvider provider;

  @Column(length = 12)
  private String currency;

  @Column(name = "balance_minor")
  private Long balanceMinor;

  @Column(name = "amount_spent_minor")
  private Long amountSpentMinor;

  @Column(name = "spend_cap_minor")
  private Long spendCapMinor;

  @Column(name = "min_campaign_group_spend_cap_minor")
  private Long minCampaignGroupSpendCapMinor;

  @Column(name = "min_daily_budget_minor")
  private Long minDailyBudgetMinor;

  @Column(name = "is_prepay_account")
  private Boolean prepayAccount;

  @Column(name = "is_tax_id_required")
  private Boolean taxIdRequired;

  @Column(name = "funding_source_id", length = 255)
  private String fundingSourceId;

  @Column(name = "funding_source_type")
  private Integer fundingSourceType;

  @Column(name = "funding_source_display", length = 500)
  private String fundingSourceDisplay;

  @Column(name = "funding_source_details_json", columnDefinition = "text")
  private String fundingSourceDetailsJson;

  @Column(name = "expired_funding_source_details_json", columnDefinition = "text")
  private String expiredFundingSourceDetailsJson;

  @Column(name = "extended_credit_invoice_group_json", columnDefinition = "text")
  private String extendedCreditInvoiceGroupJson;

  @Column(name = "tax_id_status")
  private Integer taxIdStatus;

  @Column(name = "tax_id_type", length = 120)
  private String taxIdType;

  @Column(name = "disable_reason")
  private Integer disableReason;

  @Column(name = "business_country_code", length = 8)
  private String businessCountryCode;

  @Column(name = "provider_payload_json", columnDefinition = "text")
  private String providerPayloadJson;

  @Column(name = "last_sync_at", nullable = false)
  private Instant lastSyncAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (lastSyncAt == null) lastSyncAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
