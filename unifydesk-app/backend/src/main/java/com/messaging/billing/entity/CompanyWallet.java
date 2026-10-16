package com.messaging.billing.entity;

import com.messaging.company.entity.Company;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.springframework.data.domain.Persistable;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "company_wallets")
public class CompanyWallet implements Persistable<Long> {
  @Id
  @Column(name = "company_id")
  private Long companyId;

  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "company_id")
  private Company company;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal balance = BigDecimal.ZERO;

  @Column(nullable = false, length = 3)
  private String currency = "INR";

  @Column(name = "auto_recharge_enabled", nullable = false)
  private boolean autoRechargeEnabled = false;

  @Column(name = "auto_recharge_threshold", precision = 19, scale = 4)
  private BigDecimal autoRechargeThreshold = new BigDecimal("500.0000");

  @Column(name = "auto_recharge_amount", precision = 19, scale = 4)
  private BigDecimal autoRechargeAmount = new BigDecimal("2000.0000");

  @Column(name = "auto_recharge_failed_attempts", nullable = false)
  private int autoRechargeFailedAttempts = 0;

  @Column(name = "auto_recharge_last_attempt_at")
  private Instant autoRechargeLastAttemptAt;

  @Column(name = "auto_recharge_paused", nullable = false)
  private boolean autoRechargePaused = false;

  @Version
  private Long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Transient
  private boolean isNewEntity = false;

  @Override
  public Long getId() {
    return companyId;
  }

  @Override
  public boolean isNew() {
    return isNewEntity || (companyId != null && version == null);
  }

  @PostPersist
  @PostLoad
  void markNotNew() {
    this.isNewEntity = false;
  }

  @PreUpdate
  void preUpdate() {
    this.updatedAt = Instant.now();
  }
}
