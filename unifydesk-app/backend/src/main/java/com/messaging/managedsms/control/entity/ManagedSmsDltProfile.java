package com.messaging.managedsms.control.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "managed_sms_dlt_profiles")
public class ManagedSmsDltProfile {
  @Id private Long id;
  @Column(name = "company_id", nullable = false) private Long companyId;
  @Column(name = "tenant_id", nullable = false) private Long tenantId;
  @Column(name = "legal_name", nullable = false, length = 255) private String legalName;
  @Enumerated(EnumType.STRING) @Column(name = "dlt_platform", nullable = false, length = 80) private DltPlatform dltPlatform;
  @Column(name = "pe_id", nullable = false, length = 160) private String peId;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private DltProfileStatus status;
  @Enumerated(EnumType.STRING) @Column(name = "tm_chain_status", nullable = false, length = 40) private TmChainStatus tmChainStatus;
  @Column(name = "tm_chain_reference", length = 255) private String tmChainReference;
  @Column(name = "evidence_json", columnDefinition = "text") private String evidenceJson;
  @Column(name = "ruleset_version", nullable = false, length = 80) private String rulesetVersion;
  @Column(name = "verified_at") private Instant verifiedAt;
  @Column(name = "checked_at") private Instant checkedAt;
  @Column(name = "stale_after") private Instant staleAfter;
  @Column(name = "archived_at") private Instant archivedAt;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  @Version @Column(name = "lock_version", nullable = false) private long lockVersion;

  @PrePersist void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (status == null) status = DltProfileStatus.DRAFT;
    if (tmChainStatus == null) tmChainStatus = TmChainStatus.NOT_STARTED;
    if (rulesetVersion == null || rulesetVersion.isBlank()) rulesetVersion = "INDIA_TCCCPR_2025";
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }
  @PreUpdate void beforeUpdate() { updatedAt = Instant.now(); }
}
