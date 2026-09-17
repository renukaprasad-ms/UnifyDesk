package com.messaging.company.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "permissions")
public class PermissionDefinition {

  @Id
  @Column(name = "permission_key", nullable = false, length = 120)
  private String key;

  @Column(nullable = false, length = 80)
  private String resource;

  @Column(nullable = false, length = 80)
  private String action;

  @Column(nullable = false, length = 255)
  private String description;

  @Column(nullable = false)
  private boolean assignable;
}
