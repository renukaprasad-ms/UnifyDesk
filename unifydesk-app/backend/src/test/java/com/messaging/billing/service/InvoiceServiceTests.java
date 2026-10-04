package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.billing.dto.InvoiceLineRequest;
import com.messaging.billing.entity.BillingPeriod;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Invoice;
import com.messaging.billing.entity.InvoiceLineItem;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.BillingPeriodKind;
import com.messaging.billing.enums.BillingPeriodStatus;
import com.messaging.billing.enums.InvoiceLineType;
import com.messaging.billing.enums.InvoiceStatus;
import com.messaging.billing.repository.BillingPeriodRepository;
import com.messaging.billing.repository.InvoiceLineItemRepository;
import com.messaging.billing.repository.InvoiceRepository;
import com.messaging.common.exception.ConflictException;
import com.messaging.company.entity.Company;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTests {

  @Mock private InvoiceRepository invoiceRepository;
  @Mock private InvoiceLineItemRepository lineRepository;
  @Mock private BillingPeriodRepository periodRepository;

  @InjectMocks private InvoiceService service;

  @Test
  void draftCreationLocksCanonicalPeriodAndConvergesOnExistingInvoice() {
    BillingPeriod period = platformPeriod(10L);
    Invoice existing = invoice(20L, period, InvoiceStatus.DRAFT);
    when(periodRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(period));
    when(invoiceRepository.findByBillingPeriodId(10L)).thenReturn(Optional.of(existing));

    var response = service.createDraft(period.getCompany().getId(), 10L, null);

    assertEquals("20", response.invoiceId());
    verify(invoiceRepository, never()).saveAndFlush(any(Invoice.class));
  }

  @Test
  void canonicalPlatformInvoiceReplayReturnsSameInvoiceForSameServicePeriod() {
    BillingPeriod period = platformPeriod(10L);
    Invoice existing = invoice(20L, period, InvoiceStatus.OPEN);
    existing.setSubtotal(new BigDecimal("100.00"));
    existing.setTotal(new BigDecimal("100.00"));
    existing.setAmountDue(new BigDecimal("100.00"));
    existing.setServicePeriodStart(period.getStartAt());
    existing.setServicePeriodEnd(period.getEndAt());

    InvoiceLineItem line = new InvoiceLineItem();
    line.setId(30L);
    line.setInvoice(existing);
    line.setLineType(InvoiceLineType.PLATFORM_PLAN);
    line.setAmount(new BigDecimal("100.00"));
    line.setServicePeriodStart(period.getStartAt());
    line.setServicePeriodEnd(period.getEndAt());
    line.setIdempotencyKey("PLATFORM_PERIOD:10");

    when(periodRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(period));
    when(invoiceRepository.findByBillingPeriodId(10L)).thenReturn(Optional.of(existing));
    when(lineRepository.findByInvoiceIdAndIdempotencyKey(20L, "PLATFORM_PERIOD:10"))
        .thenReturn(Optional.of(line));

    var replay =
        service.createOrResolvePlatformInvoice(1L, 10L, null, Instant.parse("2026-10-01T00:00:00Z"));

    assertEquals("20", replay.invoiceId());
    assertEquals(new BigDecimal("100.00"), replay.amountDue());
    verify(invoiceRepository, never()).saveAndFlush(any(Invoice.class));
    verify(lineRepository, never()).saveAndFlush(any(InvoiceLineItem.class));
  }

  @Test
  void closingPrepaidPlatformPeriodDoesNotCreateSecondPlatformInvoiceOrLine() {
    Instant now = Instant.parse("2026-11-01T00:00:00Z");
    BillingPeriod period = platformPeriod(10L);
    period.setStatus(BillingPeriodStatus.CLOSING);

    Invoice paid = invoice(20L, period, InvoiceStatus.PAID);
    paid.setSubtotal(new BigDecimal("100.00"));
    paid.setTotal(new BigDecimal("100.00"));
    paid.setAmountDue(BigDecimal.ZERO);
    paid.setPaidAt(Instant.parse("2026-10-01T00:05:00Z"));
    paid.setFinalizedAt(Instant.parse("2026-10-01T00:00:00Z"));
    paid.setServicePeriodStart(period.getStartAt());
    paid.setServicePeriodEnd(period.getEndAt());

    when(periodRepository.findClosingForUpdate(100)).thenReturn(List.of(period));
    when(invoiceRepository.findByBillingPeriodId(10L)).thenReturn(Optional.of(paid));
    when(lineRepository.countByInvoiceId(20L)).thenReturn(1L);

    assertEquals(1, service.recoverClosingPeriods(now));
    assertEquals(BillingPeriodStatus.FINALIZED, period.getStatus());
    verify(invoiceRepository, never()).saveAndFlush(any(Invoice.class));
    verify(lineRepository, never()).saveAndFlush(any(InvoiceLineItem.class));
  }

  @Test
  void zeroDuePlatformInvoiceIsCanonicalWithoutPaidStatus() {
    BillingPeriod period = platformPeriod(10L);
    period.setPlatformAmountSnapshot(BigDecimal.ZERO);
    period.getPlanPrice().setAmount(BigDecimal.ZERO);
    Invoice invoice = invoice(20L, period, InvoiceStatus.OPEN);
    invoice.setTotal(BigDecimal.ZERO);
    invoice.setAmountDue(BigDecimal.ZERO);
    invoice.setServicePeriodStart(period.getStartAt());
    invoice.setServicePeriodEnd(period.getEndAt());

    InvoiceLineItem line = new InvoiceLineItem();
    line.setId(30L);
    line.setInvoice(invoice);
    line.setLineType(InvoiceLineType.PLATFORM_PLAN);
    line.setAmount(BigDecimal.ZERO);
    line.setServicePeriodStart(period.getStartAt());
    line.setServicePeriodEnd(period.getEndAt());
    line.setIdempotencyKey("PLATFORM_PERIOD:10");

    when(invoiceRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(invoice));
    when(lineRepository.findByInvoiceIdAndIdempotencyKey(20L, "PLATFORM_PERIOD:10"))
        .thenReturn(Optional.of(line));

    assertEquals(period, service.requireZeroDueCanonicalPlatformInvoice(1L, 20L));
  }

  @Test
  void nonZeroPlatformInvoiceCannotUseZeroDueBoundary() {
    BillingPeriod period = platformPeriod(10L);
    Invoice invoice = invoice(20L, period, InvoiceStatus.OPEN);
    invoice.setTotal(new BigDecimal("100.00"));
    invoice.setAmountDue(new BigDecimal("100.00"));
    invoice.setServicePeriodStart(period.getStartAt());
    invoice.setServicePeriodEnd(period.getEndAt());

    InvoiceLineItem line = new InvoiceLineItem();
    line.setId(30L);
    line.setInvoice(invoice);
    line.setLineType(InvoiceLineType.PLATFORM_PLAN);
    line.setAmount(new BigDecimal("100.00"));
    line.setServicePeriodStart(period.getStartAt());
    line.setServicePeriodEnd(period.getEndAt());
    line.setIdempotencyKey("PLATFORM_PERIOD:10");

    when(invoiceRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(invoice));
    when(lineRepository.findByInvoiceIdAndIdempotencyKey(20L, "PLATFORM_PERIOD:10"))
        .thenReturn(Optional.of(line));

    assertThrows(
        ConflictException.class,
        () -> service.requireZeroDueCanonicalPlatformInvoice(1L, 20L));
  }

  @Test
  void exactInvoiceLineReplayDoesNotDuplicateCharge() {
    BillingPeriod period = platformPeriod(10L);
    Invoice invoice = invoice(20L, period, InvoiceStatus.DRAFT);
    InvoiceLineRequest request = lineRequest("usage-1", new BigDecimal("2.00"));
    InvoiceLineItem existing = line(30L, invoice, request);
    when(invoiceRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(invoice));
    when(lineRepository.findByInvoiceIdAndIdempotencyKey(20L, "usage-1"))
        .thenReturn(Optional.of(existing));

    service.addLine(20L, request);

    verify(lineRepository, never()).saveAndFlush(any(InvoiceLineItem.class));
  }

  @Test
  void sameInvoiceLineKeyWithDifferentFinancialPayloadConflicts() {
    BillingPeriod period = platformPeriod(10L);
    Invoice invoice = invoice(20L, period, InvoiceStatus.DRAFT);
    InvoiceLineRequest original = lineRequest("usage-1", new BigDecimal("2.00"));
    InvoiceLineRequest replay = lineRequest("usage-1", new BigDecimal("3.00"));
    InvoiceLineItem existing = line(30L, invoice, original);
    when(invoiceRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(invoice));
    when(lineRepository.findByInvoiceIdAndIdempotencyKey(20L, "usage-1"))
        .thenReturn(Optional.of(existing));

    assertThrows(ConflictException.class, () -> service.addLine(20L, replay));
  }

  @Test
  void finalizedInvoiceRejectsLineMutation() {
    Invoice invoice = invoice(20L, platformPeriod(10L), InvoiceStatus.OPEN);
    when(invoiceRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(invoice));

    assertThrows(
        ConflictException.class,
        () -> service.addLine(20L, lineRequest("late", BigDecimal.ONE)));
  }

  @Test
  void closingPlatformPeriodRecoversMissingDraftLineAndFinalizes() {
    Instant now = Instant.parse("2026-11-01T00:00:00Z");
    BillingPeriod period = platformPeriod(10L);
    period.setStatus(BillingPeriodStatus.CLOSING);
    when(periodRepository.findClosingForUpdate(100)).thenReturn(List.of(period));
    when(invoiceRepository.findByBillingPeriodId(10L)).thenReturn(Optional.empty());

    AtomicReference<InvoiceLineItem> savedLine = new AtomicReference<>();
    when(invoiceRepository.saveAndFlush(any(Invoice.class)))
        .thenAnswer(
            invocation -> {
              Invoice value = invocation.getArgument(0);
              if (value.getId() == null) {
                value.setId(20L);
                value.setInvoiceNumber("INV-20");
              }
              return value;
            });
    when(lineRepository.findByInvoiceIdAndIdempotencyKey(20L, "PLATFORM_PERIOD:10"))
        .thenReturn(Optional.empty());
    when(lineRepository.saveAndFlush(any(InvoiceLineItem.class)))
        .thenAnswer(
            invocation -> {
              InvoiceLineItem value = invocation.getArgument(0);
              value.setId(30L);
              savedLine.set(value);
              return value;
            });
    when(lineRepository.countByInvoiceId(20L)).thenReturn(1L);
    when(lineRepository.findByInvoiceIdOrderByCreatedAtAsc(20L))
        .thenAnswer(invocation -> List.of(savedLine.get()));

    assertEquals(1, service.recoverClosingPeriods(now));
    assertEquals(BillingPeriodStatus.FINALIZED, period.getStatus());
    assertEquals(InvoiceLineType.PLATFORM_PLAN, savedLine.get().getLineType());
    assertEquals("PLATFORM_PERIOD:10", savedLine.get().getIdempotencyKey());
  }

  @Test
  void closingManagedUsagePeriodCreatesDraftButDoesNotFinalizeEmptySnapshot() {
    Instant now = Instant.parse("2026-11-01T00:00:00Z");
    BillingPeriod period = managedPeriod(10L);
    period.setStatus(BillingPeriodStatus.CLOSING);
    when(periodRepository.findClosingForUpdate(100)).thenReturn(List.of(period));
    when(invoiceRepository.findByBillingPeriodId(10L)).thenReturn(Optional.empty());
    when(invoiceRepository.saveAndFlush(any(Invoice.class)))
        .thenAnswer(
            invocation -> {
              Invoice value = invocation.getArgument(0);
              value.setId(20L);
              value.setInvoiceNumber("INV-20");
              return value;
            });
    when(lineRepository.countByInvoiceId(20L)).thenReturn(0L);

    assertEquals(0, service.recoverClosingPeriods(now));
    assertEquals(BillingPeriodStatus.CLOSING, period.getStatus());
    verify(invoiceRepository).saveAndFlush(any(Invoice.class));
    verify(periodRepository, never()).save(period);
  }

  private InvoiceLineRequest lineRequest(String key, BigDecimal quantity) {
    return new InvoiceLineRequest(
        InvoiceLineType.MANAGED_USAGE,
        "Managed usage",
        quantity,
        new BigDecimal("10.00"),
        Instant.parse("2026-10-01T00:00:00Z"),
        Instant.parse("2026-11-01T00:00:00Z"),
        null,
        null,
        "usage:october",
        key);
  }

  private InvoiceLineItem line(Long id, Invoice invoice, InvoiceLineRequest request) {
    InvoiceLineItem line = new InvoiceLineItem();
    line.setId(id);
    line.setInvoice(invoice);
    line.setLineType(request.lineType());
    line.setDescription(request.description());
    line.setQuantity(request.quantity());
    line.setUnitAmount(request.unitAmount());
    line.setAmount(request.quantity().multiply(request.unitAmount()));
    line.setCurrency("INR");
    line.setServicePeriodStart(request.servicePeriodStart());
    line.setServicePeriodEnd(request.servicePeriodEnd());
    line.setOriginalServicePeriodStart(request.originalServicePeriodStart());
    line.setOriginalServicePeriodEnd(request.originalServicePeriodEnd());
    line.setSourceReference(request.sourceReference());
    line.setIdempotencyKey(request.idempotencyKey());
    return line;
  }

  private BillingPeriod platformPeriod(Long id) {
    BillingPeriod period = basePeriod(id, BillingPeriodKind.PLATFORM);
    Plan plan = new Plan();
    plan.setId(2L);
    PlanPrice price = new PlanPrice();
    price.setId(4L);
    price.setPlan(plan);
    price.setBillingCycle(BillingCycle.MONTHLY);
    price.setAmount(new BigDecimal("100.00"));
    price.setCurrency("INR");
    CompanySubscription subscription = new CompanySubscription();
    subscription.setId(3L);
    subscription.setPlan(plan);
    subscription.setPlanPrice(price);
    subscription.setCompany(period.getCompany());
    period.setPlan(plan);
    period.setPlanPrice(price);
    period.setSubscription(subscription);
    period.setPlatformAmountSnapshot(new BigDecimal("100.00"));
    period.setCurrencySnapshot("INR");
    return period;
  }

  private BillingPeriod managedPeriod(Long id) {
    return basePeriod(id, BillingPeriodKind.MANAGED_USAGE);
  }

  private BillingPeriod basePeriod(Long id, BillingPeriodKind kind) {
    Company company = new Company();
    company.setId(1L);
    company.setName("Acme");
    company.setBusinessEmail("billing@acme.test");
    company.setCountry("IN");
    BillingPeriod period = new BillingPeriod();
    period.setId(id);
    period.setCompany(company);
    period.setKind(kind);
    period.setCadence(BillingCycle.MONTHLY);
    period.setStartAt(Instant.parse("2026-10-01T00:00:00Z"));
    period.setEndAt(Instant.parse("2026-11-01T00:00:00Z"));
    period.setStatus(BillingPeriodStatus.OPEN);
    return period;
  }

  private Invoice invoice(Long id, BillingPeriod period, InvoiceStatus status) {
    Invoice invoice = new Invoice();
    invoice.setId(id);
    invoice.setCompany(period.getCompany());
    invoice.setBillingPeriod(period);
    invoice.setInvoiceNumber("INV-" + id);
    invoice.setStatus(status);
    invoice.setCurrency("INR");
    invoice.setCompanyNameSnapshot("Acme");
    invoice.setSubtotal(BigDecimal.ZERO);
    invoice.setDiscountAmount(BigDecimal.ZERO);
    invoice.setCreditAmount(BigDecimal.ZERO);
    invoice.setAdjustmentAmount(BigDecimal.ZERO);
    invoice.setTaxAmount(BigDecimal.ZERO);
    invoice.setTotal(BigDecimal.ZERO);
    invoice.setAmountDue(BigDecimal.ZERO);
    return invoice;
  }
}
