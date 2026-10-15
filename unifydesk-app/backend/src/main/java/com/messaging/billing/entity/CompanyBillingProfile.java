package com.messaging.billing.entity;

import com.messaging.company.entity.Company;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "company_billing_profiles")
public class CompanyBillingProfile {
  @Id
  @Column(name = "company_id")
  private Long companyId;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "company_id")
  private Company company;

  @Column(name = "legal_business_name", length = 180)
  private String legalBusinessName;

  @Column(name = "tax_id_or_gstin", length = 50)
  private String taxIdOrGstin;

  @Column(name = "billing_email", length = 254)
  private String billingEmail;

  @Column(name = "billing_phone", length = 32)
  private String billingPhone;

  @Column(name = "address_line1", length = 255)
  private String addressLine1;

  @Column(name = "address_line2", length = 255)
  private String addressLine2;

  @Column(length = 100)
  private String city;

  @Column(length = 100)
  private String state;

  @Column(name = "postal_code", length = 20)
  private String postalCode;

  @Column(length = 80, nullable = false)
  private String country = "India";

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @PreUpdate
  void preUpdate() {
    this.updatedAt = Instant.now();
  }
}
