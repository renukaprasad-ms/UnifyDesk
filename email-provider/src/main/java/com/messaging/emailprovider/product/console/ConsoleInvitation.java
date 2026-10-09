package com.messaging.emailprovider.product.console;

import com.messaging.emailprovider.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @Entity
@Table(name = "email_console_invitations")
public class ConsoleInvitation extends BaseEntity {
  @Column(name = "user_id", nullable = false)
  private Long userId;
  @Column(name = "token_hash", nullable = false, length = 64, unique = true)
  private String tokenHash;
  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;
  @Column(name = "accepted_at")
  private Instant acceptedAt;
}
