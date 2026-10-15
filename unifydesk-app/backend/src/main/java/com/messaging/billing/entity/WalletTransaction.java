package com.messaging.billing.entity;

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
    name = "wallet_transactions",
    indexes = {
      @Index(name = "idx_wallet_tx_company_time", columnList = "company_id,created_at"),
      @Index(name = "idx_wallet_tx_ref", columnList = "reference_id")
    })
public class WalletTransaction {
  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @Column(nullable = false, length = 32)
  private String type;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
  private BigDecimal balanceAfter;

  @Column(nullable = false, length = 3)
  private String currency = "INR";

  @Column(name = "reference_id", length = 120)
  private String referenceId;

  @Column(nullable = false, length = 255)
  private String description;

  @Column(name = "actor_user_id")
  private Long actorUserId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @PrePersist
  void prePersist() {
    if (id == null) {
      id = IdGenerator.nextId();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
