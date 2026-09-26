package com.messaging.ads.entity;

import com.messaging.ads.enums.AdProvider;
import com.messaging.common.id.IdGenerator;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "ad_audiences",
    uniqueConstraints = @UniqueConstraint(name = "uk_ad_audience_provider_external", columnNames = {"ad_account_id","provider","external_audience_id"}),
    indexes = {
      @Index(name = "idx_ad_audiences_company_account", columnList = "company_id,ad_account_id"),
      @Index(name = "idx_ad_audiences_account_subtype", columnList = "ad_account_id,subtype,accessible")
    })
public class AdAudience {
  @Id private Long id;
  @Column(name="company_id", nullable=false) private Long companyId;
  @Column(name="ad_account_id", nullable=false) private Long adAccountId;
  @Enumerated(EnumType.STRING) @Column(nullable=false, length=24) private AdProvider provider;
  @Column(name="external_audience_id", nullable=false, length=255) private String externalAudienceId;
  @Column(nullable=false, length=255) private String name;
  @Column(length=80) private String subtype;
  @Column(columnDefinition="text") private String description;
  @Column(name="retention_days") private Integer retentionDays;
  @Column(name="approximate_count_lower_bound") private Long approximateCountLowerBound;
  @Column(name="approximate_count_upper_bound") private Long approximateCountUpperBound;
  @Column(name="delivery_status_json", columnDefinition="text") private String deliveryStatusJson;
  @Column(name="operation_status_json", columnDefinition="text") private String operationStatusJson;
  @Column(name="pixel_id", length=255) private String pixelId;
  @Column(name="rule_json", columnDefinition="text") private String ruleJson;
  @Column(name="lookalike_spec_json", columnDefinition="text") private String lookalikeSpecJson;
  @Column(name="customer_file_source", length=64) private String customerFileSource;
  @Column(name="provider_created_at") private Instant providerCreatedAt;
  @Column(name="provider_updated_at") private Instant providerUpdatedAt;
  @Column(nullable=false) private boolean accessible = true;
  @Column(name="provider_payload_json", columnDefinition="text") private String providerPayloadJson;
  @Column(name="last_sync_at") private Instant lastSyncAt;
  @Column(name="created_at", nullable=false) private Instant createdAt;
  @Column(name="updated_at", nullable=false) private Instant updatedAt;
  @PrePersist void beforeInsert(){ if(id==null) id=IdGenerator.nextId(); Instant now=Instant.now(); if(createdAt==null) createdAt=now; updatedAt=now; }
  @PreUpdate void beforeUpdate(){ updatedAt=Instant.now(); }
}
