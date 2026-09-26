package com.messaging.managedemail.control.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.RampStage;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.SendingStatus;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.TenantStatus;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "managed_email_tenants")
public class ManagedEmailTenant {

  @Id private Long id;

  @Column(name = "company_id", nullable = false, unique = true)
  private Long companyId;

  @Column(name = "broadcast_connection_id")
  private Long broadcastConnectionId;

  @Column(nullable = false, length = 64)
  private String region;

  @Column(name = "provider_tenant_name", nullable = false, unique = true, length = 128)
  private String providerTenantName;

  @Column(
      name = "provider_configuration_set_name",
      nullable = false,
      unique = true,
      length = 128)
  private String providerConfigurationSetName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private TenantStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "sending_status", nullable = false, length = 32)
  private SendingStatus sendingStatus;

  @Column(name = "reputation_status", length = 32)
  private String reputationStatus;

  @Column(name = "provider_max_send_rate")
  private Double providerMaxSendRate;

  @Column(name = "provider_max_24h_send")
  private Double providerMax24HourSend;

  @Column(name = "provider_sent_last_24h")
  private Double providerSentLast24Hours;

  @Column(name = "provider_account_sending_enabled", nullable = false)
  private boolean providerAccountSendingEnabled;

  @Column(name = "provider_production_access_enabled", nullable = false)
  private boolean providerProductionAccessEnabled;

  @Column(name = "suspension_reason", length = 128)
  private String suspensionReason;

  @Enumerated(EnumType.STRING)
  @Column(name = "ramp_stage", nullable = false, length = 32)
  private RampStage rampStage;

  @Column(name = "last_provider_sync_at")
  private Instant lastProviderSyncAt;

  @Column(name = "suspended_at")
  private Instant suspendedAt;

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
    if (status == null) status = TenantStatus.PROVISIONING;
    if (sendingStatus == null) sendingStatus = SendingStatus.DISABLED;
    if (rampStage == null) rampStage = RampStage.NEW;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
