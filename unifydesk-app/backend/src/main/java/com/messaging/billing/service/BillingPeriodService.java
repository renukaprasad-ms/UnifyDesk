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

  private final BillingPeriodRepository periodRepository;
  private final CompanyRepository companyRepository;

  @Transactional
  public BillingPeriod ensurePlatformPeriod(CompanySubscription subscription) {
    if (subscription.getCurrentPeriodStart() == null || subscription.getCurrentPeriodEnd() == null) {
      throw new BadRequestException("Platform billing period requires exact service-period timestamps");
    }
    BillingCycle cadence =
        subscription.getPlanPrice() == null
            ? subscription.getPlan().getBillingCycle()
            : subscription.getPlanPrice().getBillingCycle();
    return ensure(
        subscription.getCompany(),
        BillingPeriodKind.PLATFORM,
        cadence,
        subscription.getCurrentPeriodStart(),
        subscription.getCurrentPeriodEnd());
  }

  @Transactional
  public BillingPeriod ensureManagedUsagePeriod(Long companyId, Instant occurredAt) {
    Company company =
        companyRepository
            .findById(companyId)
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
    for (BillingPeriod period :
        periodRepository.findByStatusAndEndAtLessThanEqualOrderByEndAtAsc(
            BillingPeriodStatus.OPEN, now)) {
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
            .findById(periodId)
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
