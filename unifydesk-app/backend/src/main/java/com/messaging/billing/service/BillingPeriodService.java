package com.messaging.billing.service;

import com.messaging.billing.dto.BillingPeriodResponse;
import com.messaging.billing.entity.BillingPeriod;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.BillingPeriodKind;
import com.messaging.billing.enums.BillingPeriodStatus;
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

  private BillingPeriod ensure(
      Company company, BillingPeriodKind kind, BillingCycle cadence, Instant start, Instant end) {
    if (start == null || end == null || !end.isAfter(start)) {
      throw new BadRequestException("Billing periods use non-empty half-open [start,end) ranges");
    }
    return periodRepository
        .findByCompanyIdAndKindAndStartAtAndEndAt(company.getId(), kind, start, end)
        .orElseGet(
            () -> {
              BillingPeriod period = new BillingPeriod();
              period.setCompany(company);
              period.setKind(kind);
              period.setCadence(cadence);
              period.setStartAt(start);
              period.setEndAt(end);
              period.setStatus(BillingPeriodStatus.OPEN);
              return periodRepository.saveAndFlush(period);
            });
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
