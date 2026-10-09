package com.messaging.emailprovider.product.model;

import com.messaging.emailprovider.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @Entity
@Table(name = "email_product_projects")
public class EmailProject extends BaseEntity {
  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 24)
  private String status = "ACTIVE";

  @Column(name = "sending_enabled", nullable = false)
  private boolean sendingEnabled = false;
}
