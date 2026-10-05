package com.messaging.billing.service;

import com.messaging.billing.dto.ResolvedPrice;
import com.messaging.billing.entity.BillingPeriod;
import com.messaging.billing.entity.UsageEvent;
import com.messaging.billing.enums.BillingCategory;
import com.messaging.billing.enums.BillingPeriodStatus;
import com.messaging.billing.enums.BillingSku;
import com.messaging.billing.enums.CommunicationDirection;
import com.messaging.billing.enums.PricingSource;
import com.messaging.billing.enums.ServiceOwnership;
import com.messaging.billing.enums.UsageUnit;
import com.messaging.billing.repository.UsageEventRepository;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageBillingService {

  private static final String R1_CURRENCY = "INR";

  private final UsageEventRepository usageEventRepository;
  private final CompanyRepository companyRepository;
  private final PricingResolver pricingResolver;
  private final BillingPeriodService billingPeriodService;

  @Autowired
  public UsageBillingService(
      UsageEventRepository usageEventRepository,
      CompanyRepository companyRepository,
      PricingResolver pricingResolver,
      BillingPeriodService billingPeriodService) {
    this.usageEventRepository = usageEventRepository;
    this.companyRepository = companyRepository;
    this.pricingResolver = pricingResolver;
    this.billingPeriodService = billingPeriodService;
  }

  UsageBillingService(
      UsageEventRepository usageEventRepository,
      CompanyRepository companyRepository,
      PricingResolver pricingResolver) {
    this(usageEventRepository, companyRepository, pricingResolver, null);
  }

  @Transactional
  public UsageEvent record(
      Long companyId,
      ServiceOwnership ownership,
      BillingSku billingSku,
      String sourceType,
      String sourceId,
      String providerReference,
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

    if (ownership == ServiceOwnership.CUSTOMER_OWNED) {
      return null;
    }
    if (ownership != ServiceOwnership.MANAGED) {
      throw new IllegalArgumentException("Service ownership is required");
    }
    validateManagedSku(billingSku, channel, unit);
    if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
    String key = required(idempotencyKey, "Idempotency key");
    Instant at = Objects.requireNonNull(occurredAt, "Financial usage occurrence time is required");
    String normalizedSourceType = required(sourceType, "Source type").toUpperCase(Locale.ROOT);
    String normalizedSourceId = required(sourceId, "Source id");

    var prior = usageEventRepository.findByIdempotencyKey(key);
    if (prior.isPresent()) {
      assertSame(
          prior.get(),
          companyId,
          ownership,
          billingSku,
          normalizedSourceType,
          normalizedSourceId,
          providerReference,
          channel,
          provider,
          billingCategory,
          direction,
          country,
          carrier,
          unit,
          quantity,
          at);
      return prior.get();
    }

    Company company =
        companyRepository
            .findById(companyId)
            .orElseThrow(() -> new NotFoundException("Company not found"));
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
    if (!R1_CURRENCY.equalsIgnoreCase(price.currency())) {
      throw new ConflictException("Billing R1 managed-service usage must resolve to INR");
    }

    BillingPeriod originalPeriod =
        billingPeriodService == null ? null : billingPeriodService.ensureManagedUsagePeriod(companyId, at);
    if (originalPeriod != null && originalPeriod.getStatus() == BillingPeriodStatus.FINALIZED) {
      billingPeriodService.ensureManagedUsagePeriod(companyId, Instant.now());
    }

    UsageEvent event = new UsageEvent();
    event.setCompany(company);
    event.setPlanIdSnapshot(parseLong(price.planId()));
    event.setSubscriptionIdSnapshot(parseLong(price.subscriptionId()));
    event.setBillingPeriod(originalPeriod);
    event.setServiceOwnership(ownership);
    event.setBillingSku(billingSku);
    event.setPricingRuleIdSnapshot(parseLong(price.pricingRuleId()));
    event.setRateCardIdSnapshot(
        price.source() == PricingSource.PLAN_RATE ? parseLong(price.pricingRuleId()) : null);
    event.setPricingSource(price.source());
    event.setSourceType(normalizedSourceType);
    event.setSourceId(normalizedSourceId);
    event.setProviderReference(normalize(providerReference));
    event.setIdempotencyKey(key);
    event.setChannel(channel);
    event.setProvider(provider);
    event.setCountry(normalizeCountry(country));
    event.setCarrier(normalize(carrier));
    event.setBillingCategory(billingCategory);
    event.setDirection(direction);
    event.setQuantity(quantity);
    event.setUnit(unit);
    event.setUnitPriceSnapshot(price.unitPrice());
    event.setTotalPrice(
        price.unitPrice().multiply(BigDecimal.valueOf(quantity)).setScale(6, RoundingMode.HALF_UP));
    event.setCurrency(price.currency().toUpperCase(Locale.ROOT));
    event.setOccurredAt(at);

    try {
      return usageEventRepository.saveAndFlush(event);
    } catch (DataIntegrityViolationException duplicate) {
      UsageEvent existing =
          usageEventRepository.findByIdempotencyKey(key).orElseThrow(() -> duplicate);
      assertSame(
          existing,
          companyId,
          ownership,
          billingSku,
          normalizedSourceType,
          normalizedSourceId,
          providerReference,
          channel,
          provider,
          billingCategory,
          direction,
          country,
          carrier,
          unit,
          quantity,
          at);
      return existing;
    }
  }

  @Deprecated
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
    throw new IllegalStateException(
        "Financial usage callers must provide explicit ServiceOwnership and BillingSku");
  }

  private void validateManagedSku(BillingSku sku, ChannelType channel, UsageUnit unit) {
    if (sku == null) throw new IllegalArgumentException("Managed financial usage requires a SKU");
    if (sku == BillingSku.MANAGED_EMAIL_OUTBOUND
        && (channel != ChannelType.EMAIL || unit != UsageUnit.MESSAGE)) {
      throw new IllegalArgumentException("Managed Email requires EMAIL/MESSAGE usage");
    }
    if (sku == BillingSku.MANAGED_SMS_SEGMENT
        && (channel != ChannelType.SMS || unit != UsageUnit.SEGMENT)) {
      throw new IllegalArgumentException("Managed SMS requires SMS/SEGMENT usage");
    }
  }

  private void assertSame(
      UsageEvent existing,
      Long companyId,
      ServiceOwnership ownership,
      BillingSku billingSku,
      String sourceType,
      String sourceId,
      String providerReference,
      ChannelType channel,
      ProviderType provider,
      BillingCategory category,
      CommunicationDirection direction,
      String country,
      String carrier,
      UsageUnit unit,
      long quantity,
      Instant at) {
    if (!existing.getCompany().getId().equals(companyId)
        || existing.getServiceOwnership() != ownership
        || existing.getBillingSku() != billingSku
        || !existing.getSourceType().equals(sourceType)
        || !existing.getSourceId().equals(sourceId)
        || !Objects.equals(existing.getProviderReference(), normalize(providerReference))
        || existing.getChannel() != channel
        || existing.getProvider() != provider
        || existing.getBillingCategory() != category
        || existing.getDirection() != direction
        || !Objects.equals(existing.getCountry(), normalizeCountry(country))
        || !Objects.equals(existing.getCarrier(), normalize(carrier))
        || existing.getUnit() != unit
        || existing.getQuantity() != quantity
        || !existing.getOccurredAt().equals(at)) {
      throw new ConflictException(
          "Financial usage idempotency key was reused for materially different usage");
    }
  }

  private Long parseLong(String value) {
    return value == null || value.isBlank() ? null : Long.valueOf(value);
  }

  private String required(String value, String name) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    return value.trim();
  }

  private String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String normalizeCountry(String value) {
    String normalized = normalize(value);
    return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
  }
}
