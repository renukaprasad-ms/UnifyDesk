package com.messaging.emailprovider.domain.entity;

import com.messaging.emailprovider.common.persistence.BaseEntity;
import com.messaging.emailprovider.domain.enums.SendingDomainStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "sending_domains",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_sending_domains_domain_name", columnNames = "domain_name")
    },
    indexes = {
      @Index(name = "idx_sending_domains_company_id", columnList = "company_id"),
      @Index(name = "idx_sending_domains_status", columnList = "status")
    })
public class SendingDomain extends BaseEntity {

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "domain_name", nullable = false, length = 253)
  private String domainName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private SendingDomainStatus status = SendingDomainStatus.PENDING;

  @Column(name = "verification_token", nullable = false, length = 128)
  private String verificationToken;

  @Column(name = "ownership_verified_at")
  private Instant ownershipVerifiedAt;

  @Column(name = "dkim_verified_at")
  private Instant dkimVerifiedAt;

  @Column(name = "last_checked_at")
  private Instant lastCheckedAt;

  @Column(name = "verified_at")
  private Instant verifiedAt;
}
