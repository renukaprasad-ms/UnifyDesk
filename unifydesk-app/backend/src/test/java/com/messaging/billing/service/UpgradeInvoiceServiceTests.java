package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.billing.dto.InvoiceResponse;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Invoice;
import com.messaging.billing.entity.InvoiceLineItem;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.entity.SubscriptionChange;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.InvoiceLineType;
import com.messaging.billing.enums.InvoiceStatus;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.SubscriptionChangeStatus;
import com.messaging.billing.enums.SubscriptionChangeType;
import com.messaging.billing.repository.InvoiceLineItemRepository;
import com.messaging.billing.repository.InvoiceRepository;
import com.messaging.billing.repository.SubscriptionChangeRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.company.entity.Company;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpgradeInvoiceServiceTests {

  @Mock private SubscriptionChangeRepository changeRepository;
  @Mock private InvoiceRepository invoiceRepository;
  @Mock private InvoiceLineItemRepository lineRepository;
  @Mock private InvoiceService invoiceService;

  private UpgradeInvoiceService service;
  private SubscriptionChange change;
  private final Instant requestedAt = Instant.parse("2026-10-03T06:00:00Z");

  @BeforeEach
  void setUp() {
    service =
        new UpgradeInvoiceService(
            changeRepository, invoiceRepository, lineRepository, invoiceService);
    change = upgradeChange(10L, 700L, new BigDecimal("500.00"));
  }

  @Test
  void positiveDueUpgradeCreatesCanonicalInvoiceFromImmutableChangeSnapshot() {
    AtomicReference<Invoice> savedInvoice = new AtomicReference<>();
    List<InvoiceLineItem> savedLines = new ArrayList<>();
    stubNewInvoiceCreation(savedInvoice, savedLines);

    InvoiceResponse response =
        service.createOrResolveUpgradeInvoice(10L, 700L, "UPG-700");

    assertEquals("900", response.invoiceId());
    assertEquals(new BigDecimal("500.00"), response.amountDue());
    assertEquals(900L, change.getInvoiceId());
    assertEquals(2, savedLines.size());

    InvoiceLineItem target =
        savedLines.stream()
            .filter(line -> line.getLineType() == InvoiceLineType.PLATFORM_UPGRADE)
            .findFirst()
            .orElseThrow();
    InvoiceLineItem credit =
        savedLines.stream()
            .filter(line -> line.getLineType() == InvoiceLineType.CREDIT)
            .findFirst()
            .orElseThrow();
    assertEquals(new BigDecimal("1000.000000"), target.getAmount());
    assertEquals(new BigDecimal("500.000000"), credit.getAmount());
    assertEquals(change.getCurrentPeriodStart(), target.getServicePeriodStart());
    assertEquals(change.getCurrentPeriodEnd(), target.getServicePeriodEnd());
  }

  @Test
  void exactReplayReturnsSameCanonicalInvoiceWithoutDuplicateFinancialLines() {
    Invoice existing = canonicalInvoice(900L, change, InvoiceStatus.OPEN);
    change.setInvoiceId(900L);
    List<InvoiceLineItem> lines = canonicalLines(existing, change);
    when(changeRepository.findByIdForUpdate(700L)).thenReturn(Optional.of(change));
    when(invoiceRepository.findBySubscriptionChangeId(700L)).thenReturn(Optional.of(existing));
    when(lineRepository.findByInvoiceIdOrderByCreatedAtAsc(900L)).thenReturn(lines);

    InvoiceResponse replay =
        service.createOrResolveUpgradeInvoice(10L, 700L, "UPG-700");

    assertEquals("900", replay.invoiceId());
    verify(invoiceRepository, never()).saveAndFlush(any(Invoice.class));
    verify(lineRepository, never()).saveAndFlush(any(InvoiceLineItem.class));
    verify(invoiceService, never()).finalizeInvoice(any(), any());
  }

  @Test
  void serializedRetryConvergesOnUniqueSubscriptionChangeInvoice() {
    Invoice existing = canonicalInvoice(900L, change, InvoiceStatus.OPEN);
    List<InvoiceLineItem> lines = canonicalLines(existing, change);
    when(changeRepository.findByIdForUpdate(700L)).thenReturn(Optional.of(change));
    when(invoiceRepository.findBySubscriptionChangeId(700L)).thenReturn(Optional.of(existing));
    when(lineRepository.findByInvoiceIdOrderByCreatedAtAsc(900L)).thenReturn(lines);
    when(changeRepository.saveAndFlush(change)).thenReturn(change);

    InvoiceResponse first =
        service.createOrResolveUpgradeInvoice(10L, 700L, "UPG-700");
    InvoiceResponse second =
        service.createOrResolveUpgradeInvoice(10L, 700L, "UPG-700");

    assertEquals(first.invoiceId(), second.invoiceId());
    assertEquals(900L, change.getInvoiceId());
    verify(invoiceRepository, never()).saveAndFlush(any(Invoice.class));
  }

  @Test
  void anotherCompanyCannotCreateUpgradeInvoice() {
    when(changeRepository.findByIdForUpdate(700L)).thenReturn(Optional.of(change));

    assertThrows(
        BadRequestException.class,
        () -> service.createOrResolveUpgradeInvoice(11L, 700L, "UPG-700"));

    verify(invoiceRepository, never()).saveAndFlush(any(Invoice.class));
  }

  @Test
  void materiallyDifferentReplayQuoteReferenceConflicts() {
    when(changeRepository.findByIdForUpdate(700L)).thenReturn(Optional.of(change));

    assertThrows(
        ConflictException.class,
        () -> service.createOrResolveUpgradeInvoice(10L, 700L, "UPG-DIFFERENT"));
  }

  @Test
  void zeroDueUpgradeDoesNotRequireFakePaidInvoice() {
    change.setAmountDue(BigDecimal.ZERO);
    change.setTargetProratedCharge(new BigDecimal("500.00"));
    change.setUnusedCurrentCredit(new BigDecimal("500.00"));
    when(changeRepository.findByIdForUpdate(700L)).thenReturn(Optional.of(change));

    assertThrows(
        BadRequestException.class,
        () -> service.createOrResolveUpgradeInvoice(10L, 700L, "UPG-700"));

    verify(invoiceRepository, never()).saveAndFlush(any(Invoice.class));
  }

  @Test
  void paymentBoundaryRejectsOpenInvoiceAndAcceptsPaidCanonicalInvoice() {
    Invoice invoice = canonicalInvoice(900L, change, InvoiceStatus.OPEN);
    List<InvoiceLineItem> lines = canonicalLines(invoice, change);
    when(invoiceRepository.findByIdForUpdate(900L)).thenReturn(Optional.of(invoice));
    when(lineRepository.findByInvoiceIdOrderByCreatedAtAsc(900L)).thenReturn(lines);

    assertThrows(
        ConflictException.class,
        () -> service.assertPaidCanonicalInvoice(change, 900L));

    invoice.setStatus(InvoiceStatus.PAID);
    invoice.setPaidAt(requestedAt.plusSeconds(60));
    invoice.setAmountDue(BigDecimal.ZERO.setScale(2));
    invoice.setTotal(new BigDecimal("500.00"));

    service.assertPaidCanonicalInvoice(change, 900L);
    assertSame(change, invoice.getSubscriptionChange());
  }

  @Test
  void existingInvoiceWithDifferentFinancialSnapshotFailsClosed() {
    Invoice invoice = canonicalInvoice(900L, change, InvoiceStatus.OPEN);
    List<InvoiceLineItem> lines = canonicalLines(invoice, change);
    lines.get(0).setAmount(new BigDecimal("999.00"));
    when(changeRepository.findByIdForUpdate(700L)).thenReturn(Optional.of(change));
    when(invoiceRepository.findBySubscriptionChangeId(700L)).thenReturn(Optional.of(invoice));
    when(lineRepository.findByInvoiceIdOrderByCreatedAtAsc(900L)).thenReturn(lines);

    assertThrows(
        ConflictException.class,
        () -> service.createOrResolveUpgradeInvoice(10L, 700L, "UPG-700"));
  }

  private void stubNewInvoiceCreation(
      AtomicReference<Invoice> savedInvoice, List<InvoiceLineItem> savedLines) {
    when(changeRepository.findByIdForUpdate(700L)).thenReturn(Optional.of(change));
    when(invoiceRepository.findBySubscriptionChangeId(700L)).thenReturn(Optional.empty());
    when(invoiceRepository.saveAndFlush(any(Invoice.class)))
        .thenAnswer(
            invocation -> {
              Invoice invoice = invocation.getArgument(0);
              invoice.setId(900L);
              invoice.setInvoiceNumber("INV-900");
              savedInvoice.set(invoice);
              return invoice;
            });
    when(lineRepository.saveAndFlush(any(InvoiceLineItem.class)))
        .thenAnswer(
            invocation -> {
              InvoiceLineItem line = invocation.getArgument(0);
              line.setId(1000L + savedLines.size());
              savedLines.add(line);
              return line;
            });
    when(invoiceService.finalizeInvoice(900L, requestedAt))
        .thenAnswer(
            invocation -> {
              Invoice invoice = savedInvoice.get();
              invoice.setStatus(InvoiceStatus.OPEN);
              invoice.setSubtotal(new BigDecimal("1000.00"));
              invoice.setCreditAmount(new BigDecimal("500.00"));
              invoice.setDiscountAmount(BigDecimal.ZERO);
              invoice.setAdjustmentAmount(BigDecimal.ZERO);
              invoice.setTaxAmount(BigDecimal.ZERO);
              invoice.setTotal(new BigDecimal("500.00"));
              invoice.setAmountDue(new BigDecimal("500.00"));
              invoice.setFinalizedAt(requestedAt);
              invoice.setDueAt(requestedAt);
              return response(invoice);
            });
    when(invoiceRepository.findByIdForUpdate(900L))
        .thenAnswer(invocation -> Optional.of(savedInvoice.get()));
    when(lineRepository.findByInvoiceIdOrderByCreatedAtAsc(900L))
        .thenAnswer(invocation -> savedLines);
    when(changeRepository.saveAndFlush(change)).thenReturn(change);
  }

  private SubscriptionChange upgradeChange(Long companyId, Long changeId, BigDecimal amountDue) {
    Company company = new Company();
    company.setId(companyId);
    company.setName("Acme");
    company.setBusinessEmail("billing@acme.test");
    company.setCountry("IN");

    Plan fromPlan = new Plan();
    fromPlan.setId(20L);
    Plan targetPlan = new Plan();
    targetPlan.setId(30L);

    PlanPrice fromPrice = new PlanPrice();
    fromPrice.setId(21L);
    fromPrice.setPlan(fromPlan);
    fromPrice.setBillingCycle(BillingCycle.MONTHLY);
    fromPrice.setAmount(new BigDecimal("1000.00"));
    fromPrice.setCurrency("INR");
    fromPrice.setStatus(RateCardStatus.ACTIVE);

    PlanPrice targetPrice = new PlanPrice();
    targetPrice.setId(31L);
    targetPrice.setPlan(targetPlan);
    targetPrice.setBillingCycle(BillingCycle.MONTHLY);
    targetPrice.setAmount(new BigDecimal("2000.00"));
    targetPrice.setCurrency("INR");
    targetPrice.setStatus(RateCardStatus.ACTIVE);

    CompanySubscription subscription = new CompanySubscription();
    subscription.setId(40L);
    subscription.setCompany(company);
    subscription.setPlan(fromPlan);
    subscription.setPlanPrice(fromPrice);

    SubscriptionChange value = new SubscriptionChange();
    value.setId(changeId);
    value.setCompany(company);
    value.setSubscription(subscription);
    value.setFromPlan(fromPlan);
    value.setToPlan(targetPlan);
    value.setFromPlanPrice(fromPrice);
    value.setToPlanPrice(targetPrice);
    value.setChangeType(SubscriptionChangeType.UPGRADE);
    value.setStatus(SubscriptionChangeStatus.PENDING_PAYMENT);
    value.setRequestedAt(requestedAt);
    value.setEffectiveAt(requestedAt);
    value.setQuoteReference("UPG-" + changeId);
    value.setCurrency("INR");
    value.setCurrentPeriodStart(Instant.parse("2026-10-01T00:00:00Z"));
    value.setCurrentPeriodEnd(Instant.parse("2026-11-01T00:00:00Z"));
    value.setTargetProratedCharge(new BigDecimal("1000.00"));
    value.setUnusedCurrentCredit(new BigDecimal("500.00"));
    value.setAmountDue(amountDue);
    return value;
  }

  private Invoice canonicalInvoice(
      Long id, SubscriptionChange source, InvoiceStatus status) {
    Invoice invoice = new Invoice();
    invoice.setId(id);
    invoice.setCompany(source.getCompany());
    invoice.setSubscriptionChange(source);
    invoice.setSourceReference(source.getQuoteReference());
    invoice.setInvoiceNumber("INV-" + id);
    invoice.setStatus(status);
    invoice.setCurrency("INR");
    invoice.setCompanyNameSnapshot("Acme");
    invoice.setSubtotal(new BigDecimal("1000.00"));
    invoice.setDiscountAmount(BigDecimal.ZERO);
    invoice.setCreditAmount(new BigDecimal("500.00"));
    invoice.setAdjustmentAmount(BigDecimal.ZERO);
    invoice.setTaxAmount(BigDecimal.ZERO);
    invoice.setTotal(new BigDecimal("500.00"));
    invoice.setAmountDue(
        status == InvoiceStatus.PAID
            ? BigDecimal.ZERO.setScale(2)
            : source.getAmountDue());
    invoice.setServicePeriodStart(source.getCurrentPeriodStart());
    invoice.setServicePeriodEnd(source.getCurrentPeriodEnd());
    return invoice;
  }

  private List<InvoiceLineItem> canonicalLines(
      Invoice invoice, SubscriptionChange source) {
    InvoiceLineItem target = new InvoiceLineItem();
    target.setInvoice(invoice);
    target.setLineType(InvoiceLineType.PLATFORM_UPGRADE);
    target.setAmount(source.getTargetProratedCharge());
    target.setServicePeriodStart(source.getCurrentPeriodStart());
    target.setServicePeriodEnd(source.getCurrentPeriodEnd());

    InvoiceLineItem credit = new InvoiceLineItem();
    credit.setInvoice(invoice);
    credit.setLineType(InvoiceLineType.CREDIT);
    credit.setAmount(source.getUnusedCurrentCredit());
    credit.setServicePeriodStart(source.getCurrentPeriodStart());
    credit.setServicePeriodEnd(source.getCurrentPeriodEnd());
    return new ArrayList<>(List.of(target, credit));
  }

  private InvoiceResponse response(Invoice invoice) {
    return new InvoiceResponse(
        invoice.getId().toString(),
        invoice.getCompany().getId().toString(),
        null,
        invoice.getInvoiceNumber(),
        invoice.getStatus(),
        invoice.getCurrency(),
        invoice.getSubtotal(),
        invoice.getDiscountAmount(),
        invoice.getCreditAmount(),
        invoice.getAdjustmentAmount(),
        invoice.getTaxAmount(),
        invoice.getTotal(),
        invoice.getAmountDue(),
        invoice.getServicePeriodStart(),
        invoice.getServicePeriodEnd(),
        invoice.getFinalizedAt(),
        invoice.getDueAt(),
        invoice.getPaidAt());
  }
}
