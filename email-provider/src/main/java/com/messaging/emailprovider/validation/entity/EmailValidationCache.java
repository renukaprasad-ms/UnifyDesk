package com.messaging.emailprovider.validation.entity;

import com.messaging.emailprovider.common.persistence.BaseEntity;
import com.messaging.emailprovider.validation.enums.SmtpProbeStatus;
import com.messaging.emailprovider.validation.enums.ValidationReason;
import com.messaging.emailprovider.validation.enums.ValidationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    name = "email_validation_cache",
    indexes = {@Index(name = "idx_validation_cache_expires", columnList = "expires_at")})
public class EmailValidationCache extends BaseEntity {

  @Column(name = "normalized_email", nullable = false, unique = true, length = 320)
  private String normalizedEmail;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ValidationStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 64)
  private ValidationReason reason;

  @Column(nullable = false)
  private int score;

  @Column(name = "domain_routable", nullable = false)
  private boolean domainRoutable;

  @Column(name = "mx_found", nullable = false)
  private boolean mxFound;

  @Column(nullable = false)
  private boolean disposable;

  @Column(name = "role_address", nullable = false)
  private boolean roleAddress;

  @Column(name = "catch_all")
  private Boolean catchAll;

  @Enumerated(EnumType.STRING)
  @Column(name = "smtp_status", length = 32)
  private SmtpProbeStatus smtpStatus;

  @Column(name = "smtp_code")
  private Integer smtpCode;

  @Column(length = 1024)
  private String diagnostic;

  @Column(name = "validated_at", nullable = false)
  private Instant validatedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;
}
