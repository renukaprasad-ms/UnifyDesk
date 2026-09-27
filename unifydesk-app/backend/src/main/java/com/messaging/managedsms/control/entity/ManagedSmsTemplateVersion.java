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
@Table(name = "managed_sms_template_versions")
public class ManagedSmsTemplateVersion {
  @Id private Long id;
  @Column(name = "company_id", nullable = false) private Long companyId;
  @Column(name = "template_id", nullable = false) private Long templateId;
  @Column(name = "version_no", nullable = false) private long versionNo;
  @Column(name = "dlt_template_id", nullable = false, length = 255) private String dltTemplateId;
  @Column(name = "body_text", nullable = false, columnDefinition = "text") private String bodyText;
  @Column(name = "body_fingerprint", nullable = false, length = 128) private String bodyFingerprint;
  @Column(name = "variables_schema_json", nullable = false, columnDefinition = "text") private String variablesSchemaJson;
  @Column(name = "cta_policy_json", nullable = false, columnDefinition = "text") private String ctaPolicyJson;
  @Column(name = "consent_policy_json", columnDefinition = "text") private String consentPolicyJson;
  @Column(name = "opt_out_policy_json", columnDefinition = "text") private String optOutPolicyJson;
  @Column(name = "ruleset_version", nullable = false, length = 80) private String rulesetVersion;
  @Enumerated(EnumType.STRING) @Column(name = "dlt_status", nullable = false, length = 40) private DltTemplateStatus dltStatus;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private TemplateVersionStatus status;
  @Column(name = "approved_at") private Instant approvedAt;
  @Column(name = "checked_at") private Instant checkedAt;
  @Column(name = "stale_after") private Instant staleAfter;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Version @Column(name = "lock_version", nullable = false) private long lockVersion;

  @PrePersist void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (dltStatus == null) dltStatus = DltTemplateStatus.DRAFT;
    if (status == null) status = TemplateVersionStatus.DRAFT;
    if (rulesetVersion == null || rulesetVersion.isBlank()) rulesetVersion = "INDIA_TCCCPR_2025";
    if (createdAt == null) createdAt = Instant.now();
  }
}
