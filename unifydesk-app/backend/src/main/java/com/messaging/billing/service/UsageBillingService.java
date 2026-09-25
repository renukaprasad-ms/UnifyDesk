package com.messaging.billing.service;

import com.messaging.billing.dto.ResolvedPrice;
import com.messaging.billing.entity.UsageEvent;
import com.messaging.billing.enums.BillingCategory;
import com.messaging.billing.enums.CommunicationDirection;
import com.messaging.billing.enums.UsageUnit;
import com.messaging.billing.repository.UsageEventRepository;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsageBillingService {

  private final UsageEventRepository usageEventRepository;
  private final CompanyRepository companyRepository;
  private final PricingResolver pricingResolver;

  @Transactional
  public UsageEvent record(
      Long companyId,
      String sourceType,
      String sourceId,
      String idempotencyKey,
      ChannelType channel,
      ProviderType provider,
      BillingCategory billingCategory,
      CommunicationDirection direction,
      String country,
      String carrier,
      UsageUnit unit,
      long quantity,
      Instant occurredAt) {

    if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Idempotency key is required");
    }

    return usageEventRepository
        .findByIdempotencyKey(idempotencyKey)
        .orElseGet(
            () -> {
              Company company =
                  companyRepository
                      .findById(companyId)
                      .orElseThrow(() -> new NotFoundException("Company not found"));
              Instant at = occurredAt == null ? Instant.now() : occurredAt;
              ResolvedPrice price =
                  pricingResolver.resolve(
                      companyId,
                      channel,
                      provider,
                      billingCategory,
                      direction,
                      country,
                      carrier,
                      unit,
                      at);

              UsageEvent event = new UsageEvent();
              event.setCompany(company);
              event.setPlanIdSnapshot(Long.parseLong(price.planId()));
              event.setPricingRuleIdSnapshot(Long.parseLong(price.pricingRuleId()));
              event.setRateCardIdSnapshot(
                  price.source() == com.messaging.billing.enums.PricingSource.PLAN_RATE
                      ? Long.parseLong(price.pricingRuleId())
                      : null);
              event.setPricingSource(price.source());
              event.setSourceType(sourceType.trim().toUpperCase(java.util.Locale.ROOT));
              event.setSourceId(sourceId.trim());
              event.setIdempotencyKey(idempotencyKey.trim());
              event.setChannel(channel);
              event.setProvider(provider);
              event.setCountry(
                  country == null || country.isBlank()
                      ? null
                      : country.trim().toUpperCase(java.util.Locale.ROOT));
              event.setCarrier(carrier == null || carrier.isBlank() ? null : carrier.trim());
              event.setBillingCategory(billingCategory);
              event.setDirection(direction);
              event.setQuantity(quantity);
              event.setUnit(unit);
              event.setUnitPriceSnapshot(price.unitPrice());
              event.setTotalPrice(price.unitPrice().multiply(BigDecimal.valueOf(quantity)));
              event.setCurrency(price.currency());
              event.setOccurredAt(at);

              try {
                return usageEventRepository.saveAndFlush(event);
              } catch (DataIntegrityViolationException duplicate) {
                return usageEventRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> duplicate);
              }
            });
  }
}
