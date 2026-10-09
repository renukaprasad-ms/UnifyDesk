package com.messaging.emailprovider.product.console;

import com.messaging.emailprovider.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @Entity
@Table(name = "email_console_users")
public class ConsoleUser extends BaseEntity {
  @Column(name = "organization_id", nullable = false)
  private Long organizationId;
  @Column(nullable = false, length = 320)
  private String email;
  @Column(name = "display_name", nullable = false, length = 120)
  private String displayName;
  @Column(name = "password_hash", length = 255)
  private String passwordHash;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ConsoleRole role;
  @Column(nullable = false, length = 16)
  private String status = "PENDING";
}
