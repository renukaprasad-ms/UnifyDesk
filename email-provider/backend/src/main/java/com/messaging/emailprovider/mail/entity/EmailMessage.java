package com.messaging.emailprovider.mail.entity;

import com.messaging.emailprovider.common.persistence.BaseEntity;
import com.messaging.emailprovider.mail.enums.EmailMessageStatus;
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
    name = "email_messages",
    indexes = {
      @Index(name = "idx_email_messages_company_id", columnList = "company_id"),
      @Index(name = "idx_email_messages_sending_domain_id", columnList = "sending_domain_id"),
      @Index(name = "idx_email_messages_status", columnList = "status"),
      @Index(name = "idx_email_messages_created_at", columnList = "created_at")
    })
public class EmailMessage extends BaseEntity {

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "sending_domain_id", nullable = false)
  private Long sendingDomainId;

  @Column(name = "from_address", nullable = false, length = 320)
  private String fromAddress;

  @Column(name = "reply_to_address", length = 320)
  private String replyToAddress;

  @Column(nullable = false, length = 998)
  private String subject;

  @Column(name = "text_body", columnDefinition = "TEXT")
  private String textBody;

  @Column(name = "html_body", columnDefinition = "TEXT")
  private String htmlBody;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private EmailMessageStatus status = EmailMessageStatus.ACCEPTED;

  @Column(name = "idempotency_key", length = 128)
  private String idempotencyKey;

  @Column(name = "request_hash", length = 64)
  private String requestHash;

  @Column(name = "marketing", nullable = false)
  private boolean marketing;

  @Column(name = "consent_reference", length = 128)
  private String consentReference;

  @Column(name = "accepted_at")
  private Instant acceptedAt;
}
