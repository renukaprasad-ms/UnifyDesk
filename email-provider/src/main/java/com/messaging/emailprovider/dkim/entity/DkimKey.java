package com.messaging.emailprovider.dkim.entity;

import com.messaging.emailprovider.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "dkim_keys",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_dkim_keys_domain_selector",
          columnNames = {"sending_domain_id", "selector"})
    },
    indexes = {
      @Index(name = "idx_dkim_keys_sending_domain_id", columnList = "sending_domain_id"),
      @Index(name = "idx_dkim_keys_active", columnList = "active")
    })
public class DkimKey extends BaseEntity {

  @Column(name = "sending_domain_id", nullable = false)
  private Long sendingDomainId;

  @Column(nullable = false, length = 63)
  private String selector;

  @Column(name = "encrypted_private_key", nullable = false, columnDefinition = "TEXT")
  private String encryptedPrivateKey;

  @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
  private String publicKey;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "verified_at")
  private Instant verifiedAt;
}
