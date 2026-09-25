package com.messaging.emailprovider.validation.entity;

import com.messaging.emailprovider.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "email_validation_domain_cache",
    indexes = {@Index(name = "idx_validation_domain_cache_expires", columnList = "expires_at")})
public class EmailValidationDomainCache extends BaseEntity {

  @Column(nullable = false, unique = true, length = 253)
  private String domain;

  @Column(name = "mail_routable", nullable = false)
  private boolean mailRoutable;

  @Column(name = "null_mx", nullable = false)
  private boolean nullMx;

  @Column(name = "mx_found", nullable = false)
  private boolean mxFound;

  @Column(name = "mx_hosts", columnDefinition = "TEXT")
  private String mxHosts;

  @Column(nullable = false)
  private boolean disposable;

  @Column(name = "catch_all")
  private Boolean catchAll;

  @Column(name = "dns_lookup_failed", nullable = false)
  private boolean dnsLookupFailed;

  @Column(name = "checked_at", nullable = false)
  private Instant checkedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;
}
