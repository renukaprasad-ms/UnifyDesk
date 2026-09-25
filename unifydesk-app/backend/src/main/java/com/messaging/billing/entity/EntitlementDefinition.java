package com.messaging.billing.entity;

import com.messaging.billing.enums.EntitlementCategory;
import com.messaging.billing.enums.EntitlementValueType;
import com.messaging.common.id.IdGenerator;
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
    name = "entitlement_definitions",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_entitlement_definitions_key", columnNames = "entitlement_key"))
public class EntitlementDefinition {
  @Id private Long id;

  @Column(name = "entitlement_key", nullable = false, length = 120)
  private String key;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(length = 500)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "value_type", nullable = false, length = 32)
  private EntitlementValueType valueType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private EntitlementCategory category;

  @Column(name = "default_value", nullable = false, length = 512)
  private String defaultValue;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
