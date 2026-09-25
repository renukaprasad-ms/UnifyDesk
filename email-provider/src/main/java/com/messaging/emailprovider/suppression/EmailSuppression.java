package com.messaging.emailprovider.suppression;

import com.messaging.emailprovider.common.persistence.BaseEntity;
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
    name = "email_suppressions",
    indexes = {
      @Index(
          name = "idx_email_suppressions_lookup",
          columnList = "company_id,recipient_address,active")
    })
public class EmailSuppression extends BaseEntity {

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "recipient_address", nullable = false, length = 320)
  private String recipientAddress;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private SuppressionReason reason;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "expires_at")
  private Instant expiresAt;
}
