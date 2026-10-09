package com.messaging.emailprovider.product.model;

import com.messaging.emailprovider.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @Entity
@Table(name = "email_product_api_keys")
public class EmailApiKey extends BaseEntity {
  @Column(name = "project_id", nullable = false)
  private Long projectId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "lookup_prefix", nullable = false, unique = true, length = 24)
  private String lookupPrefix;

  @Column(name = "secret_hash", nullable = false, length = 64)
  private String secretHash;

  @Column(nullable = false, length = 160)
  private String scopes;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;
}
