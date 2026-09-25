package com.messaging.ads.entity;

import com.messaging.ads.enums.AdProvider;
import com.messaging.common.id.IdGenerator;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "ad_conversions",
    uniqueConstraints = @UniqueConstraint(name = "uk_ad_conversion_provider_external", columnNames = {"ad_account_id","provider","external_conversion_id"}),
    indexes = {
      @Index(name = "idx_ad_conversions_company_account", columnList = "company_id,ad_account_id"),
      @Index(name = "idx_ad_conversions_account_archived", columnList = "ad_account_id,archived,accessible")
    })
public class AdConversion {
  @Id private Long id;
  @Column(name="company_id", nullable=false) private Long companyId;
  @Column(name="ad_account_id", nullable=false) private Long adAccountId;
  @Enumerated(EnumType.STRING) @Column(nullable=false, length=24) private AdProvider provider;
  @Column(name="external_conversion_id", nullable=false, length=255) private String externalConversionId;
  @Column(nullable=false, length=255) private String name;
  @Column(columnDefinition="text") private String description;
  @Column(name="custom_event_type", length=80) private String customEventType;
  @Column(name="event_source_type", length=80) private String eventSourceType;
  @Column(name="pixel_id", length=255) private String pixelId;
  @Column(name="rule_json", columnDefinition="text") private String ruleJson;
  @Column(name="data_sources_json", columnDefinition="text") private String dataSourcesJson;
  @Column(name="default_conversion_value", precision=19, scale=4) private BigDecimal defaultConversionValue;
  @Column(nullable=false) private boolean archived;
  @Column(nullable=false) private boolean unavailable;
  @Column(name="first_fired_at") private Instant firstFiredAt;
  @Column(name="last_fired_at") private Instant lastFiredAt;
  @Column(name="provider_created_at") private Instant providerCreatedAt;
  @Column(nullable=false) private boolean accessible = true;
  @Column(name="provider_payload_json", columnDefinition="text") private String providerPayloadJson;
  @Column(name="last_sync_at") private Instant lastSyncAt;
  @Column(name="created_at", nullable=false) private Instant createdAt;
  @Column(name="updated_at", nullable=false) private Instant updatedAt;
  @PrePersist void beforeInsert(){ if(id==null) id=IdGenerator.nextId(); Instant now=Instant.now(); if(createdAt==null) createdAt=now; updatedAt=now; }
  @PreUpdate void beforeUpdate(){ updatedAt=Instant.now(); }
}
