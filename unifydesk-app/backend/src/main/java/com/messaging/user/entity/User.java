package com.messaging.user.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.media.entity.Media;
import com.messaging.user.enums.UserStatus;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "users",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
      @UniqueConstraint(name = "uk_users_username", columnNames = "username")
    },
    indexes = {@Index(name = "idx_users_purge_due", columnList = "status,purge_after")})
public class User {

  @Id private Long id;

  @Column(nullable = false, unique = true, length = 320)
  private String email;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(name = "is_verified", nullable = false)
  private boolean verified;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "profile_picture_media_id")
  private Media profilePicture;

  @Column(nullable = false, unique = true, length = 50)
  private String username;

  @Column(nullable = false, length = 255)
  private String password;

  @Column(name = "two_factor_enabled", nullable = false)
  private boolean twoFactorEnabled;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private UserStatus status = UserStatus.ACTIVE;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "purge_after")
  private Instant purgeAfter;

  @Column(name = "deletion_requested_by")
  private Long deletionRequestedBy;

  @Column(name = "purge_started_at")
  private Instant purgeStartedAt;

  @Column(name = "purged_at")
  private Instant purgedAt;

  @PrePersist
  private void assignId() {
    if (id == null) {
      id = IdGenerator.nextId();
    }
  }
}
