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
    name = "email_validation_items",
    indexes = {
      @Index(name = "idx_validation_items_job_id", columnList = "job_id,id"),
      @Index(name = "idx_validation_items_batch_id", columnList = "batch_id,id"),
      @Index(name = "idx_validation_items_domain", columnList = "domain")
    })
public class EmailValidationItem extends BaseEntity {
  @Column(name = "job_id", nullable = false)
  private Long jobId;

  @Column(name = "batch_id", nullable = false)
  private Long batchId;

  @Column(name = "original_email", nullable = false, length = 1024)
  private String originalEmail;

  @Column(name = "dedupe_key", nullable = false, length = 64)
  private String dedupeKey;

  @Column(name = "normalized_email", length = 320)
  private String normalizedEmail;

  @Column(length = 253)
  private String domain;

  @Enumerated(EnumType.STRING)
  @Column(length = 32)
  private ValidationStatus status;

  @Enumerated(EnumType.STRING)
  @Column(length = 64)
  private ValidationReason reason;

  @Column
  private Integer score;

  @Column(name = "syntax_valid")
  private Boolean syntaxValid;

  @Column(name = "domain_routable")
  private Boolean domainRoutable;

  @Column(name = "mx_found")
  private Boolean mxFound;

  @Column
  private Boolean disposable;

  @Column(name = "role_address")
  private Boolean roleAddress;

  @Column(name = "catch_all")
  private Boolean catchAll;

  @Enumerated(EnumType.STRING)
  @Column(name = "smtp_status", length = 32)
  private SmtpProbeStatus smtpStatus;

  @Column(name = "smtp_code")
  private Integer smtpCode;

  @Column(length = 1024)
  private String diagnostic;

  @Column(name = "validated_at")
  private Instant validatedAt;
}
