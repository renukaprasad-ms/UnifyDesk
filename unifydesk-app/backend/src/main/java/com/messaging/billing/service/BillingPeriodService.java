package com.messaging.billing.service;

import com.messaging.billing.dto.BillingPeriodResponse;
import com.messaging.billing.entity.BillingPeriod;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.BillingPeriodKind;
import com.messaging.billing.enums.BillingPeriodStatus;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.repository.BillingPeriodRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillingPeriodService {

  private static final int CLAIM_BATCH_SIZE = 100;

  private final BillingPeriodRepository periodRepository;
  private final CompanyRepository companyRepository;

  @Transactional
  public BillingPeriod ensurePlatformPeriod(CompanySubscription subscription) {
    if (subscription.getCurrentPeriodStart() == null || subscription.getCurrentPeriodEnd() == null) {
      throw new BadRequestException(
          "Platform billing period requires exact service-period timestamps");
    }

    Company company =
        companyRepository
            .findByIdForUpdate(subscription.getCompany().getId())
            .orElseThrow(() -> new NotFoundException("Company not found"));

    BillingCycle cadence =
        subscription.getPlanPrice() == null
            ? subscription.getPlan().getBillingCycle()
            : subscription.getPlanPrice().getBillingCycle();
    BigDecimal amount =
        subscription.getPlanPrice() == null
            ? subscription.getPlan().getBasePrice()
            : subscription.getPlanPrice().getAmount();
    String currency =
        subscription.getPlanPrice() == null
            ? subscription.getPlan().getCurrency()
            : subscription.getPlanPrice().getCurrency();

    BillingPeriod period =
        ensure(
            company,
            BillingPeriodKind.PLATFORM,
            cadence,
            subscription.getCurrentPeriodStart(),
            subscription.getCurrentPeriodEnd());
    assertCompatiblePlatformSnapshot(period, subscription, amount, currency);
    if (period.getSubscription() == null
        || period.getPlan() == null
        || period.getPlatformAmountSnapshot() == null
        || period.getCurrencySnapshot() == null) {
      period.setSubscription(subscription);
      period.setPlan(subscription.getPlan());
      period.setPlanPrice(subscription.getPlanPrice());
      period.setPlatformAmountSnapshot(amount);
      period.setCurrencySnapshot(currency);
      period = periodRepository.saveAndFlush(period);
    }
    return period;
  }

  /**
   * Creates/resolves the immutable PLATFORM financial period before a subscription exists.
   *
   * <p>Used by B1 checkout/renewal invoice contracts. B2 must never derive platform period dates or
   * amounts itself.
   */
  @Transactional
  public BillingPeriod ensurePlatformPeriodSnapshot(
      Company company, Plan plan, PlanPrice price, Instant start, Instant end) {
    if (company == null || plan == null || price == null) {
      throw new BadRequestException("Platform period snapshot requires company, plan and price");
    }
    if (!price.getPlan().getId().equals(plan.getId()) || price.getStatus() != RateCardStatus.ACTIVE) {
      throw new BadRequestException("Platform period PlanPrice does not belong to the plan");
    }
    if (!"INR".equalsIgnoreCase(price.getCurrency())) {
      throw new BadRequestException("Billing R1 supports INR only");
    }
    FinancialEffectiveTime.assertStoredActiveRange(
        price.getEffectiveFrom(), price.getEffectiveTo(), "PlanPrice");
    if (start == null
        || !FinancialEffectiveTime.includes(price.getEffectiveFrom(), price.getEffectiveTo(), start)) {
      throw new BadRequestException("Platform period PlanPrice is not effective at period start");
    }
    Instant canonicalEnd = nextBoundary(start, price.getBillingCycle());
    if (price.getBillingCycle() == BillingCycle.FREE) {
      if (end != null) {
        throw new BadRequestException("FREE platform period must be open-ended");
      }
    } else if (canonicalEnd == null || !canonicalEnd.equals(end)) {
      throw new BadRequestException(
          "Prepaid platform period must follow the selected PlanPrice billing cycle");
    }
    Company locked =
        companyRepository
            .findByIdForUpdate(company.getId())
            .orElseThrow(() -> new NotFoundException("Company not found"));
    List<BillingPeriod> existingBoundary =
        periodRepository.findByCompanyIdAndKindAndStartAt(
            locked.getId(), BillingPeriodKind.PLATFORM, start);
    if (existingBoundary.size() > 1) {
      throw new ConflictException(
          "Multiple PLATFORM billing periods exist for the same commercial boundary");
    }
    BillingPeriod period;
    if (existingBoundary.isEmpty()) {
      period = ensure(locked, BillingPeriodKind.PLATFORM, price.getBillingCycle(), start, end);
    } else {
      period = existingBoundary.getFirst();
      if (!Objects.equals(period.getEndAt(), end)) {
        throw new ConflictException(
            "A different immutable PLATFORM period already owns this commercial boundary");
      }
    }
    assertCompatibleCommercialSnapshot(period, plan, price, price.getAmount(), price.getCurrency());
    if (period.getPlan() == null
        || period.getPlanPrice() == null
        || period.getPlatformAmountSnapshot() == null
        || period.getCurrencySnapshot() == null) {
      period.setPlan(plan);
      period.setPlanPrice(price);
      period.setPlatformAmountSnapshot(price.getAmount());
      period.setCurrencySnapshot(price.getCurrency());
      period = periodRepository.saveAndFlush(period);
    }
    return period;
  }

  /**
   * Binds a pre-created/prepaid PLATFORM period to the activated subscription without rewriting its
   * immutable commercial snapshot.
   */
  @Transactional
  public BillingPeriod bindPlatformPeriodToSubscription(
      Long periodId, CompanySubscription subscription) {
    BillingPeriod period =
        periodRepository
            .findByIdForUpdate(periodId)
            .orElseThrow(() -> new NotFoundException("Billing period not found"));
    if (period.getKind() != BillingPeriodKind.PLATFORM) {
      throw new BadRequestException("Only PLATFORM periods can bind a subscription");
    }
    if (!period.getCompany().getId().equals(subscription.getCompany().getId())
        || !period.getStartAt().equals(subscription.getCurrentPeriodStart())
        || !Objects.equals(period.getEndAt(), subscription.getCurrentPeriodEnd())) {
      throw new ConflictException("Platform period does not match subscription service range");
    }
    BigDecimal amount =
        subscription.getPlanPrice() == null
            ? subscription.getPlan().getBasePrice()
            : subscription.getPlanPrice().getAmount();
    String currency =
        subscription.getPlanPrice() == null
            ? subscription.getPlan().getCurrency()
            : subscription.getPlanPrice().getCurrency();
    assertCompatiblePlatformSnapshot(period, subscription, amount, currency);
    if (period.getSubscription() == null) {
      period.setSubscription(subscription);
      return periodRepository.saveAndFlush(period);
    }
    return period;
  }

  @Transactional
  public BillingPeriod ensureManagedUsagePeriod(Long companyId, Instant occurredAt) {
    Company company =
        companyRepository
            .findByIdForUpdate(companyId)
            .orElseThrow(() -> new NotFoundException("Company not found"));
    ZonedDateTime instant = occurredAt.atZone(ZoneOffset.UTC);
    Instant start =
        instant.withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant end = start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
    return ensure(company, BillingPeriodKind.MANAGED_USAGE, BillingCycle.MONTHLY, start, end);
  }

  @Transactional
  public int markDueClosing(Instant now) {
    int changed = 0;
    for (BillingPeriod period : periodRepository.findDueOpenForUpdate(now, CLAIM_BATCH_SIZE)) {
      if (period.getStatus() != BillingPeriodStatus.OPEN) {
        continue;
      }
      period.setStatus(BillingPeriodStatus.CLOSING);
      periodRepository.save(period);
      changed++;
    }
    periodRepository.flush();
    return changed;
  }

  @Transactional
  public BillingPeriod finalizePeriod(Long periodId, Instant finalizedAt) {
    BillingPeriod period =
        periodRepository
            .findByIdForUpdate(periodId)
            .orElseThrow(() -> new NotFoundException("Billing period not found"));
    if (period.getStatus() == BillingPeriodStatus.FINALIZED) {
      return period;
    }
    if (period.getStatus() != BillingPeriodStatus.CLOSING) {
      throw new ConflictException("Only a closing billing period can be finalized");
    }
    period.setStatus(BillingPeriodStatus.FINALIZED);
    period.setFinalizedAt(finalizedAt);
    return periodRepository.saveAndFlush(period);
  }

  @Transactional(readOnly = true)
  public List<BillingPeriodResponse> periods(Long companyId) {
    return periodRepository.findByCompanyIdOrderByStartAtDesc(companyId).stream()
        .map(this::toResponse)
        .toList();
  }

  private void assertCompatiblePlatformSnapshot(
      BillingPeriod period,
      CompanySubscription subscription,
      BigDecimal amount,
      String currency) {
    if (period.getSubscription() != null
        && !period.getSubscription().getId().equals(subscription.getId())) {
      throw new ConflictException("Billing period is already bound to another subscription");
    }
    if (period.getPlan() != null
        && !period.getPlan().getId().equals(subscription.getPlan().getId())) {
      throw new ConflictException("Billing period is already bound to another plan");
    }
    if (period.getPlanPrice() != null
        && (subscription.getPlanPrice() == null
            || !period.getPlanPrice().getId().equals(subscription.getPlanPrice().getId()))) {
      throw new ConflictException("Billing period is already bound to another plan price");
    }
    if (period.getPlatformAmountSnapshot() != null
        && period.getPlatformAmountSnapshot().compareTo(amount) != 0) {
      throw new ConflictException("Billing period financial snapshot is immutable");
    }
    if (period.getCurrencySnapshot() != null
        && !period.getCurrencySnapshot().equalsIgnoreCase(currency)) {
      throw new ConflictException("Billing period currency snapshot is immutable");
    }
  }

  private void assertCompatibleCommercialSnapshot(
      BillingPeriod period,
      Plan plan,
      PlanPrice price,
      BigDecimal amount,
      String currency) {
    if (period.getPlan() != null && !period.getPlan().getId().equals(plan.getId())) {
      throw new ConflictException("Billing period is already bound to another plan");
    }
    if (period.getPlanPrice() != null && !period.getPlanPrice().getId().equals(price.getId())) {
      throw new ConflictException("Billing period is already bound to another plan price");
    }
    if (period.getPlatformAmountSnapshot() != null
        && period.getPlatformAmountSnapshot().compareTo(amount) != 0) {
      throw new ConflictException("Billing period financial snapshot is immutable");
    }
    if (period.getCurrencySnapshot() != null
        && !period.getCurrencySnapshot().equalsIgnoreCase(currency)) {
      throw new ConflictException("Billing period currency snapshot is immutable");
    }
  }

  private Instant nextBoundary(Instant start, BillingCycle cycle) {
    ZonedDateTime utc = start.atZone(ZoneOffset.UTC);
    return switch (cycle) {
      case MONTHLY -> utc.plusMonths(1).toInstant();
      case YEARLY -> utc.plusYears(1).toInstant();
      case FREE -> null;
    };
  }

  private BillingPeriod ensure(
      Company company, BillingPeriodKind kind, BillingCycle cadence, Instant start, Instant end) {
    if (start == null
        || (cadence == BillingCycle.FREE && end != null)
        || (cadence != BillingCycle.FREE && (end == null || !end.isAfter(start)))) {
      throw new BadRequestException(
          "Billing periods require [start,end), except FREE which is open-ended");
    }

    if (cadence == BillingCycle.FREE) {
      List<BillingPeriod> existing =
          periodRepository.findByCompanyIdAndKindAndStartAt(company.getId(), kind, start);
      if (existing.size() > 1) {
        throw new ConflictException(
            "Multiple open-ended billing periods exist for the same commercial boundary");
      }
      if (!existing.isEmpty()) {
        BillingPeriod period = existing.getFirst();
        if (period.getEndAt() != null || period.getCadence() != BillingCycle.FREE) {
          throw new ConflictException(
              "A different immutable billing period already owns this boundary");
        }
        return period;
      }
    } else {
      var existing =
          periodRepository.findByCompanyIdAndKindAndStartAtAndEndAt(
              company.getId(), kind, start, end);
      if (existing.isPresent()) {
        return existing.get();
      }
    }

    BillingPeriod period = new BillingPeriod();
    period.setCompany(company);
    period.setKind(kind);
    period.setCadence(cadence);
    period.setStartAt(start);
    period.setEndAt(end);
    period.setStatus(BillingPeriodStatus.OPEN);
    return periodRepository.saveAndFlush(period);
  }

  private BillingPeriodResponse toResponse(BillingPeriod period) {
    return new BillingPeriodResponse(
        period.getId().toString(),
        period.getCompany().getId().toString(),
        period.getKind(),
        period.getCadence(),
        period.getStartAt(),
        period.getEndAt(),
        period.getStatus(),
        period.getFinalizedAt());
  }
}
