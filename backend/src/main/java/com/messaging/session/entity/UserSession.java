package com.messaging.session.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
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
    name = "user_sessions",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_user_sessions_user_platform",
          columnNames = {"user_id", "platform"})
    })
public class UserSession {

  @Id
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SessionPlatform platform;

  @Column(name = "refresh_token_id", nullable = false, unique = true)
  private String refreshTokenId;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent", length = 512)
  private String userAgent;

  @Column(name = "device_name", length = 120)
  private String deviceName;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt = Instant.now();

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  public boolean isActive() {
    return revokedAt == null && expiresAt != null && expiresAt.isAfter(Instant.now());
  }

  @PrePersist
  private void assignId() {
    if (id == null) {
      id = IdGenerator.nextId();
    }
  }
}
