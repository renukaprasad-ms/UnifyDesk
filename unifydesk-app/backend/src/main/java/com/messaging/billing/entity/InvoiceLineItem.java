package com.messaging.billing.entity;

import com.messaging.billing.enums.InvoiceLineType;
import com.messaging.common.id.IdGenerator;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Immutable
@Table(
    name = "invoice_line_items",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_invoice_line_items_idempotency",
          columnNames = {"invoice_id", "idempotency_key"})
    },
    indexes = {@Index(name = "idx_invoice_line_items_invoice", columnList = "invoice_id")})
public class InvoiceLineItem {
  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "invoice_id", nullable = false)
  private Invoice invoice;

  @Enumerated(EnumType.STRING)
  @Column(name = "line_type", nullable = false, length = 64)
  private InvoiceLineType lineType;

  @Column(nullable = false, length = 500)
  private String description;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal quantity;

  @Column(name = "unit_amount", nullable = false, precision = 19, scale = 6)
  private BigDecimal unitAmount;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "service_period_start")
  private Instant servicePeriodStart;

  @Column(name = "service_period_end")
  private Instant servicePeriodEnd;

  @Column(name = "original_service_period_start")
  private Instant originalServicePeriodStart;

  @Column(name = "original_service_period_end")
  private Instant originalServicePeriodEnd;

  @Column(name = "source_reference", length = 255)
  private String sourceReference;

  @Column(name = "idempotency_key", nullable = false, length = 255)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "retention_until", nullable = false, insertable = false, updatable = false)
  private Instant retentionUntil;

  @Column(name = "legal_hold", nullable = false)
  private boolean legalHold;

  @PrePersist
  private void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (createdAt == null) createdAt = Instant.now();
  }
}
