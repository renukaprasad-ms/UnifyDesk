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
@Table(name = "managed_sms_headers")
public class ManagedSmsHeader {
  @Id private Long id;
  @Column(name = "company_id", nullable = false) private Long companyId;
  @Column(name = "dlt_profile_id", nullable = false) private Long dltProfileId;
  @Column(nullable = false, length = 32) private String header;
  @Enumerated(EnumType.STRING) @Column(name = "message_category", nullable = false, length = 32) private MessageCategory messageCategory;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private HeaderStatus status;
  @Enumerated(EnumType.STRING) @Column(name = "provider_mapping_status", nullable = false, length = 40) private ProviderMappingStatus providerMappingStatus;
  @Column(name = "approved_at") private Instant approvedAt;
  @Column(name = "checked_at") private Instant checkedAt;
  @Column(name = "stale_after") private Instant staleAfter;
  @Column(name = "rejection_reason", length = 255) private String rejectionReason;
  @Column(name = "archived_at") private Instant archivedAt;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  @Version @Column(name = "lock_version", nullable = false) private long lockVersion;

  @PrePersist void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (status == null) status = HeaderStatus.DRAFT;
    if (providerMappingStatus == null) providerMappingStatus = ProviderMappingStatus.NOT_STARTED;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }
  @PreUpdate void beforeUpdate() { updatedAt = Instant.now(); }
}
