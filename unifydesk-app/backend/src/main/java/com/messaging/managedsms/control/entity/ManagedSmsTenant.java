package com.messaging.managedsms.control.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.ProviderMode;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.TenantStatus;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "managed_sms_tenants")
public class ManagedSmsTenant {
  @Id private Long id;
  @Column(name = "company_id", nullable = false, unique = true) private Long companyId;
  @Column(name = "broadcast_connection_id", nullable = false, unique = true) private Long broadcastConnectionId;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private TenantStatus status;
  @Column(name = "provider_account_ref", length = 255) private String providerAccountRef;
  @Column(name = "provider_client_ref", length = 255) private String providerClientRef;
  @Enumerated(EnumType.STRING) @Column(name = "provider_mode", nullable = false, length = 32) private ProviderMode providerMode;
  @Column(name = "sending_enabled", nullable = false) private boolean sendingEnabled;
  @Column(name = "suspended_reason", length = 255) private String suspendedReason;
  @Column(name = "last_provider_sync_at") private Instant lastProviderSyncAt;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  @Version @Column(name = "lock_version", nullable = false) private long lockVersion;

  @PrePersist void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (status == null) status = TenantStatus.ONBOARDING;
    if (providerMode == null) providerMode = ProviderMode.CHILD_PER_COMPANY;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }
  @PreUpdate void beforeUpdate() { updatedAt = Instant.now(); }
}
