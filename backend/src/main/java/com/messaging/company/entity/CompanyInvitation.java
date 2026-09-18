package com.messaging.company.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.company.enums.CompanyInvitationStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "company_invitations",
    indexes = {
      @Index(name = "idx_company_invitations_company_status", columnList = "company_id,status"),
      @Index(name = "idx_company_invitations_expires", columnList = "expires_at")
    })
public class CompanyInvitation {

  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @Column(nullable = false, length = 320)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private CompanyInvitationStatus status;

  @Column(name = "token_hash", nullable = false, length = 64, unique = true)
  private String tokenHash;

  @Column(name = "invited_by_user_id", nullable = false)
  private Long invitedByUserId;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "accepted_by_user_id")
  private Long acceptedByUserId;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "company_invitation_roles",
      joinColumns = @JoinColumn(name = "invitation_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  private Set<CompanyCustomRole> roles = new LinkedHashSet<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  private void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  private void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
