package com.messaging.billing.service;

import com.messaging.billing.entity.UsageEvent;
import com.messaging.billing.enums.PricingSource;
import com.messaging.billing.repository.UsageEventRepository;
import com.messaging.common.exception.BadRequestException;
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
  public UsageEvent record(UsageChargeRequest request) {
    validate(request);

    return usageEventRepository
        .findByIdempotencyKey(request.idempotencyKey())
        .orElseGet(() -> createUsageEvent(request));
  }

  private UsageEvent createUsageEvent(UsageChargeRequest request) {
    ResolvedPrice resolved =
        pricingResolver.resolve(
            new PricingRequest(
                request.companyId(),
                request.channel(),
                request.provider(),
                request.billingCategory(),
                request.direction(),
                request.country(),
                request.carrier(),
                request.unit(),
                request.occurredAt()));

    Company company =
        companyRepository
            .findById(request.companyId())
            .orElseThrow(() -> new NotFoundException("Company not found"));

    UsageEvent event = new UsageEvent();
    event.setCompany(company);
    event.setPlanIdSnapshot(Long.parseLong(resolved.planId()));
    event.setPricingSource(resolved.source());
    event.setPricingRuleIdSnapshot(Long.parseLong(resolved.pricingRuleId()));
    if (resolved.source() == PricingSource.PLAN_RATE) {
      event.setRateCardIdSnapshot(Long.parseLong(resolved.pricingRuleId()));
    }
    event.setChannel(request.channel());
    event.setProvider(request.provider());
    event.setCountry(normalizeCountry(request.country()));
    event.setCarrier(normalizeOptional(request.carrier()));
    event.setBillingCategory(request.billingCategory());
    event.setDirection(request.direction());
    event.setQuantity(request.quantity());
    event.setUnit(request.unit());
    event.setUnitPriceSnapshot(resolved.unitPrice());
    event.setTotalPrice(resolved.unitPrice().multiply(BigDecimal.valueOf(request.quantity())));
    event.setCurrency(resolved.currency());
    event.setSourceType(normalizeOptional(request.sourceType()));
    event.setSourceId(normalizeOptional(request.sourceId()));
    event.setIdempotencyKey(request.idempotencyKey().trim());
    event.setOccurredAt(request.occurredAt() == null ? Instant.now() : request.occurredAt());

    try {
      return usageEventRepository.saveAndFlush(event);
    } catch (DataIntegrityViolationException duplicate) {
      return usageEventRepository
          .findByIdempotencyKey(request.idempotencyKey().trim())
          .orElseThrow(() -> duplicate);
    }
  }

  private void validate(UsageChargeRequest request) {
    if (request == null || request.companyId() == null) {
      throw new BadRequestException("Usage charge request is incomplete");
    }
    if (request.quantity() <= 0) {
      throw new BadRequestException("Usage quantity must be greater than zero");
    }
    if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
      throw new BadRequestException("Idempotency key is required");
    }
  }

  private String normalizeCountry(String value) {
    return value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
  }

  private String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
