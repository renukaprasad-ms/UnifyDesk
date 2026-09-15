package com.messaging.company.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.company.enums.CompanyStatus;
import com.messaging.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
    name = "companies",
    uniqueConstraints = {@UniqueConstraint(name = "uk_companies_slug", columnNames = "slug")},
    indexes = {
      @Index(name = "idx_companies_status", columnList = "status"),
      @Index(name = "idx_companies_created_by", columnList = "created_by_user_id")
    })
public class Company {

  @Id private Long id;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false, unique = true, length = 180)
  private String slug;

  @Column(name = "business_email", length = 254)
  private String businessEmail;

  @Column(length = 80)
  private String country;

  @Column(length = 80)
  private String timezone;

  @Column(name = "logo_media_id")
  private Long logoMediaId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private CompanyStatus status;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by_user_id", nullable = false)
  private User createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @PrePersist
  private void beforeInsert() {
    if (id == null) {
      id = IdGenerator.nextId();
    }
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  private void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
