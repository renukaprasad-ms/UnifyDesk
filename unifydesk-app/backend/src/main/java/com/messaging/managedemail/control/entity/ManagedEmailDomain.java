package com.messaging.managedemail.control.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.*;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "managed_email_domains")
public class ManagedEmailDomain {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "domain_type", nullable = false, length = 32)
  private DomainType domainType;

  @Column(name = "normalized_domain", nullable = false, length = 253)
  private String normalizedDomain;

  @Column(name = "display_domain", nullable = false, length = 253)
  private String displayDomain;

  @Column(name = "hosted_slug", length = 63)
  private String hostedSlug;

  @Column(name = "hosted_reservation_token", length = 32)
  private String hostedReservationToken;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private DomainStatus status;

  @Column(name = "provider_identity_name", length = 320)
  private String providerIdentityName;

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_status", nullable = false, length = 32)
  private VerificationStatus verificationStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "dkim_status", nullable = false, length = 32)
  private DkimStatus dkimStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "dmarc_status", nullable = false, length = 32)
  private DmarcStatus dmarcStatus;

  @Column(name = "dmarc_policy", length = 32)
  private String dmarcPolicy;

  @Column(name = "last_dns_check_at")
  private Instant lastDnsCheckAt;

  @Column(name = "last_provider_sync_at")
  private Instant lastProviderSyncAt;

  @Column(name = "verification_failed_at")
  private Instant verificationFailedAt;

  @Column(name = "activated_at")
  private Instant activatedAt;

  @Column(name = "suspended_at")
  private Instant suspendedAt;

  @Column(name = "released_at")
  private Instant releasedAt;

  @Column(name = "requested_subdomain", length = 63)
  private String requestedSubdomain;

  @Column(name = "rejection_reason", length = 500)
  private String rejectionReason;

  @Column(name = "reviewed_by_user_id")
  private Long reviewedByUserId;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "admin_assigned_alternative", nullable = false)
  private boolean adminAssignedAlternative;

  @Column(name = "created_by_user_id", nullable = false)
  private Long createdByUserId;

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
    if (status == null) status = DomainStatus.PROVISIONING;
    if (verificationStatus == null) verificationStatus = VerificationStatus.PENDING;
    if (dkimStatus == null) dkimStatus = DkimStatus.PENDING;
    if (dmarcStatus == null) dmarcStatus = DmarcStatus.UNKNOWN;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }

  public boolean isTerminal() {
    return status == DomainStatus.DELETED
        || status == DomainStatus.RETIRED
        || status == DomainStatus.REJECTED;
  }
}
