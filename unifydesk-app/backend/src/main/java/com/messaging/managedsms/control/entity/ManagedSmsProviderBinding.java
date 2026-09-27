package com.messaging.managedsms.control.entity;

import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.common.id.IdGenerator;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.ProviderBindingStatus;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "managed_sms_provider_bindings")
public class ManagedSmsProviderBinding {
  @Id private Long id;
  @Column(name = "company_id", nullable = false) private Long companyId;
  @Column(name = "tenant_id", nullable = false) private Long tenantId;
  @Column(name = "template_version_id", nullable = false) private Long templateVersionId;
  @Column(name = "header_id", nullable = false) private Long headerId;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private BroadcastSenderProvider provider;
  @Column(name = "provider_template_ref", length = 255) private String providerTemplateRef;
  @Column(name = "provider_flow_ref", length = 255) private String providerFlowRef;
  @Column(name = "provider_mapping_ref", length = 255) private String providerMappingRef;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private ProviderBindingStatus status;
  @Column(name = "last_sync_at") private Instant lastSyncAt;
  @Column(name = "rejection_reason", length = 255) private String rejectionReason;
  @Version @Column(nullable = false) private long version;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  @PrePersist void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (provider == null) provider = BroadcastSenderProvider.MSG91_SMS;
    if (status == null) status = ProviderBindingStatus.PENDING;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }
  @PreUpdate void beforeUpdate() { updatedAt = Instant.now(); }
}
