package com.messaging.billing.entity;

import com.messaging.billing.enums.EntitlementCategory;
import com.messaging.billing.enums.EntitlementValueType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "entitlement_definitions")
public class EntitlementDefinition {

  @Id
  @Column(name = "entitlement_key", length = 120)
  private String key;

  @Column(nullable = false, length = 160)
  private String label;

  @Column(length = 500)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "value_type", nullable = false, length = 32)
  private EntitlementValueType valueType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private EntitlementCategory category;

  @Column(name = "default_value", length = 512)
  private String defaultValue;

  @Column(name = "allow_unlimited", nullable = false)
  private boolean allowUnlimited;

  @Column(nullable = false)
  private boolean active;
}
