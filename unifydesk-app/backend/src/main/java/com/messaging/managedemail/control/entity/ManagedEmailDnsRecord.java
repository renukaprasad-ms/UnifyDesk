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
@Table(name = "managed_email_dns_records")
public class ManagedEmailDnsRecord {

  @Id private Long id;

  @Column(name = "domain_id", nullable = false)
  private Long domainId;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Enumerated(EnumType.STRING)
  @Column(name = "record_purpose", nullable = false, length = 32)
  private DnsRecordPurpose recordPurpose;

  @Enumerated(EnumType.STRING)
  @Column(name = "record_type", nullable = false, length = 16)
  private DnsRecordType recordType;

  @Column(name = "record_name", nullable = false, length = 512)
  private String recordName;

  @Column(name = "record_value", nullable = false, length = 2048)
  private String recordValue;

  @Column private Integer priority;

  @Column(nullable = false)
  private boolean required;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private DnsRecordStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private DnsRecordSource source;

  @Column(name = "last_checked_at")
  private Instant lastCheckedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (status == null) status = DnsRecordStatus.PENDING;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
