package com.messaging.billing.entity;

import com.messaging.billing.enums.InvoiceStatus;
import com.messaging.common.id.IdGenerator;
import com.messaging.company.entity.Company;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "invoices",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_invoices_number", columnNames = "invoice_number"),
      @UniqueConstraint(name = "uk_invoices_billing_period", columnNames = "billing_period_id")
    },
    indexes = {
      @Index(name = "idx_invoices_company_time", columnList = "company_id,created_at"),
      @Index(name = "idx_invoices_period", columnList = "billing_period_id")
    })
public class Invoice {
  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "billing_period_id")
  private BillingPeriod billingPeriod;

  @Column(name = "invoice_number", nullable = false, length = 80)
  private String invoiceNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private InvoiceStatus status;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "company_name_snapshot", nullable = false, length = 160)
  private String companyNameSnapshot;

  @Column(name = "business_email_snapshot", length = 254)
  private String businessEmailSnapshot;

  @Column(name = "billing_address_line1", length = 255)
  private String billingAddressLine1;

  @Column(name = "billing_address_line2", length = 255)
  private String billingAddressLine2;

  @Column(name = "billing_city", length = 120)
  private String billingCity;

  @Column(name = "billing_state", length = 120)
  private String billingState;

  @Column(name = "billing_postal_code", length = 32)
  private String billingPostalCode;

  @Column(name = "billing_country", length = 80)
  private String billingCountry;

  @Column(name = "tax_id_snapshot", length = 120)
  private String taxIdSnapshot;

  @Column(name = "tax_registration_snapshot", length = 120)
  private String taxRegistrationSnapshot;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal subtotal = BigDecimal.ZERO;

  @Column(name = "discount_amount", nullable = false, precision = 19, scale = 6)
  private BigDecimal discountAmount = BigDecimal.ZERO;

  @Column(name = "credit_amount", nullable = false, precision = 19, scale = 6)
  private BigDecimal creditAmount = BigDecimal.ZERO;

  @Column(name = "adjustment_amount", nullable = false, precision = 19, scale = 6)
  private BigDecimal adjustmentAmount = BigDecimal.ZERO;

  @Column(name = "tax_amount", nullable = false, precision = 19, scale = 6)
  private BigDecimal taxAmount = BigDecimal.ZERO;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal total = BigDecimal.ZERO;

  @Column(name = "amount_due", nullable = false, precision = 19, scale = 6)
  private BigDecimal amountDue = BigDecimal.ZERO;

  @Column(name = "service_period_start")
  private Instant servicePeriodStart;

  @Column(name = "service_period_end")
  private Instant servicePeriodEnd;

  @Column(name = "finalized_at")
  private Instant finalizedAt;

  @Column(name = "due_at")
  private Instant dueAt;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "retention_until", nullable = false, insertable = false, updatable = false)
  private Instant retentionUntil;

  @Column(name = "legal_hold", nullable = false)
  private boolean legalHold;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  private void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (invoiceNumber == null) invoiceNumber = "INV-" + id;
    if (status == null) status = InvoiceStatus.DRAFT;
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  private void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
