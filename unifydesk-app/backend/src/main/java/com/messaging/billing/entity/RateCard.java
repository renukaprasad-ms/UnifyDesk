package com.messaging.billing.entity;

import com.messaging.billing.enums.BillingCategory;
import com.messaging.billing.enums.CommunicationDirection;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.UsageUnit;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.common.id.IdGenerator;
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
    name = "rate_cards",
    indexes = {
      @Index(name = "idx_rate_cards_plan_channel", columnList = "plan_id,channel,status"),
      @Index(name = "idx_rate_cards_effective", columnList = "effective_from,effective_to")
    })
public class RateCard {

  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_id", nullable = false)
  private Plan plan;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ChannelType channel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 80)
  private ProviderType provider;

  @Column(length = 2)
  private String country;

  @Column(length = 80)
  private String carrier;

  @Enumerated(EnumType.STRING)
  @Column(name = "message_type", nullable = false, length = 64)
  private BillingCategory billingCategory;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private CommunicationDirection direction;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private UsageUnit unit;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal price;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  @Column(name = "effective_to")
  private Instant effectiveTo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private RateCardStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  private void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  private void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
