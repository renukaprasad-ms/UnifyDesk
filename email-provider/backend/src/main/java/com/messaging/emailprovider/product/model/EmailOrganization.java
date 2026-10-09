package com.messaging.emailprovider.product.model;

import com.messaging.emailprovider.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @Entity
@Table(name = "email_product_organizations")
public class EmailOrganization extends BaseEntity {
  @Column(nullable = false, length = 120)
  private String name;

  @Column(name = "contact_email", nullable = false, length = 320)
  private String contactEmail;

  @Column(nullable = false, length = 24)
  private String status = "ACTIVE";
}
