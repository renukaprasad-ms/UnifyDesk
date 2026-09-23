package com.messaging.ads.entity;

import com.messaging.ads.enums.AdEntityStatus;
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
    name = "ad_keywords",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_ad_keyword_provider_external",
          columnNames = {"ad_account_id", "provider", "external_keyword_id"})
    },
    indexes = {
      @Index(name = "idx_ad_keywords_company_ad_set", columnList = "company_id,ad_set_id"),
      @Index(name = "idx_ad_keywords_ad_set_status", columnList = "ad_set_id,status,accessible")
    })
public class AdKeyword {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "ad_account_id", nullable = false)
  private Long adAccountId;

  @Column(name = "campaign_id", nullable = false)
  private Long campaignId;

  @Column(name = "ad_set_id", nullable = false)
  private Long adSetId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdProvider provider;

  @Column(name = "external_keyword_id", nullable = false, length = 255)
  private String externalKeywordId;

  @Column(name = "keyword_text", nullable = false, length = 500)
  private String keywordText;

  @Column(name = "match_type", length = 40)
  private String matchType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdEntityStatus status;

  @Column(name = "provider_status", length = 40)
  private String providerStatus;

  @Column(name = "bid_amount_micros")
  private Long bidAmountMicros;

  @Column(name = "quality_score")
  private Integer qualityScore;

  @Column(name = "final_urls_json", columnDefinition = "text")
  private String finalUrlsJson;

  @Column(nullable = false)
  private boolean negative;

  @Column(nullable = false)
  private boolean accessible = true;

  @Column(name = "provider_payload_json", columnDefinition = "text")
  private String providerPayloadJson;

  @Column(name = "last_sync_at")
  private Instant lastSyncAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (status == null) status = AdEntityStatus.UNKNOWN;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
