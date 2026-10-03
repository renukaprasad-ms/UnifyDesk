package com.messaging.billing.service;

import com.messaging.billing.dto.InvoiceIdentitySnapshot;
import com.messaging.billing.dto.InvoiceLineRequest;
import com.messaging.billing.dto.InvoiceResponse;
import com.messaging.billing.entity.BillingPeriod;
import com.messaging.billing.entity.Invoice;
import com.messaging.billing.entity.InvoiceLineItem;
import com.messaging.billing.enums.BillingPeriodKind;
import com.messaging.billing.enums.BillingPeriodStatus;
import com.messaging.billing.enums.InvoiceLineType;
import com.messaging.billing.enums.InvoiceStatus;
import com.messaging.billing.repository.BillingPeriodRepository;
import com.messaging.billing.repository.InvoiceLineItemRepository;
import com.messaging.billing.repository.InvoiceRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.entity.Company;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceService {

  private static final String R1_CURRENCY = "INR";
  private static final int CLAIM_BATCH_SIZE = 100;

  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineItemRepository lineRepository;
  private final BillingPeriodRepository periodRepository;

  @Transactional
  public InvoiceResponse createDraft(
      Long companyId, Long billingPeriodId, InvoiceIdentitySnapshot identity) {
    BillingPeriod period =
        periodRepository
            .findByIdForUpdate(billingPeriodId)
            .orElseThrow(() -> new NotFoundException("Billing period not found"));
    if (!period.getCompany().getId().equals(companyId)) {
      throw new BadRequestException("Billing period does not belong to the company");
    }
    return toResponse(ensureDraftForLockedPeriod(period, identity));
  }

  @Transactional
  public InvoiceResponse addLine(Long invoiceId, InvoiceLineRequest request) {
    Invoice invoice = requireDraftForUpdate(invoiceId);
    String key = normalizeRequired(request.idempotencyKey(), "Invoice line idempotency key");

    var existing = lineRepository.findByInvoiceIdAndIdempotencyKey(invoiceId, key);
    if (existing.isPresent()) {
      assertSameLine(existing.get(), request);
      return toResponse(invoice);
    }

    validateServiceRange(request.servicePeriodStart(), request.servicePeriodEnd());
    if (request.lineType() == InvoiceLineType.PRIOR_PERIOD_USAGE_ADJUSTMENT) {
      validateServiceRange(
          request.originalServicePeriodStart(), request.originalServicePeriodEnd());
      if (request.originalServicePeriodStart() == null
          || request.originalServicePeriodEnd() == null) {
        throw new BadRequestException(
            "Prior-period adjustments require the original service-period range");
      }
    }

    BigDecimal amount =
        request.quantity().multiply(request.unitAmount()).setScale(6, RoundingMode.HALF_UP);
    InvoiceLineItem line = new InvoiceLineItem();
    line.setInvoice(invoice);
    line.setLineType(request.lineType());
    line.setDescription(request.description().trim());
    line.setQuantity(request.quantity());
    line.setUnitAmount(request.unitAmount());
    line.setAmount(amount);
    line.setCurrency(R1_CURRENCY);
    line.setServicePeriodStart(request.servicePeriodStart());
    line.setServicePeriodEnd(request.servicePeriodEnd());
    line.setOriginalServicePeriodStart(request.originalServicePeriodStart());
    line.setOriginalServicePeriodEnd(request.originalServicePeriodEnd());
    line.setSourceReference(normalize(request.sourceReference()));
    line.setIdempotencyKey(key);
    lineRepository.saveAndFlush(line);
    return toResponse(invoice);
  }

  @Transactional
  public InvoiceResponse finalizeInvoice(Long invoiceId, Instant dueAt) {
    Invoice invoice =
        invoiceRepository
            .findByIdForUpdate(invoiceId)
            .orElseThrow(() -> new NotFoundException("Invoice not found"));
    if (invoice.getStatus() != InvoiceStatus.DRAFT) {
      return toResponse(invoice);
    }
    if (lineRepository.countByInvoiceId(invoiceId) == 0) {
      throw new ConflictException("Cannot finalize an invoice without a financial snapshot");
    }
    return toResponse(finalizeLocked(invoice, dueAt));
  }

  @Transactional
  public InvoiceResponse markPaidConfirmed(Long invoiceId, Instant paidAt) {
    Invoice invoice =
        invoiceRepository
            .findByIdForUpdate(invoiceId)
            .orElseThrow(() -> new NotFoundException("Invoice not found"));
    if (invoice.getStatus() == InvoiceStatus.PAID) {
      return toResponse(invoice);
    }
    if (invoice.getStatus() != InvoiceStatus.OPEN) {
      throw new ConflictException("Only an open invoice can be marked paid");
    }
    invoice.setStatus(InvoiceStatus.PAID);
    invoice.setPaidAt(paidAt);
    invoice.setAmountDue(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    return toResponse(invoiceRepository.saveAndFlush(invoice));
  }

  @Transactional
  public int recoverClosingPeriods(Instant now) {
    int finalized = 0;
    for (BillingPeriod period : periodRepository.findClosingForUpdate(CLAIM_BATCH_SIZE)) {
      Invoice invoice = ensureDraftForLockedPeriod(period, null);

      if (period.getKind() == BillingPeriodKind.PLATFORM) {
        if (!hasPlatformSnapshot(period)) {
          continue;
        }
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
          ensurePlatformLine(invoice, period);
        }
      }

      long lineCount = lineRepository.countByInvoiceId(invoice.getId());
      if (lineCount == 0) {
        continue;
      }
      if (invoice.getStatus() == InvoiceStatus.DRAFT) {
        invoice = finalizeLocked(invoice, now);
      }

      period.setStatus(BillingPeriodStatus.FINALIZED);
      period.setFinalizedAt(
          invoice.getFinalizedAt() == null ? now : invoice.getFinalizedAt());
      periodRepository.save(period);
      finalized++;
    }
    periodRepository.flush();
    return finalized;
  }

  @Transactional(readOnly = true)
  public List<InvoiceResponse> invoices(Long companyId) {
    return invoiceRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
        .map(this::toResponse)
        .toList();
  }

  private Invoice ensureDraftForLockedPeriod(
      BillingPeriod period, InvoiceIdentitySnapshot identity) {
    var existing = invoiceRepository.findByBillingPeriodId(period.getId());
    if (existing.isPresent()) {
      return existing.get();
    }

    Company company = period.getCompany();
    Invoice invoice = new Invoice();
    invoice.setCompany(company);
    invoice.setBillingPeriod(period);
    invoice.setCurrency(R1_CURRENCY);
    invoice.setStatus(InvoiceStatus.DRAFT);
    invoice.setCompanyNameSnapshot(
        identity != null && hasText(identity.companyName())
            ? identity.companyName().trim()
            : company.getName());
    invoice.setBusinessEmailSnapshot(
        identity != null && hasText(identity.businessEmail())
            ? identity.businessEmail().trim()
            : company.getBusinessEmail());
    invoice.setBillingAddressLine1(value(identity, Field.ADDRESS1));
    invoice.setBillingAddressLine2(value(identity, Field.ADDRESS2));
    invoice.setBillingCity(value(identity, Field.CITY));
    invoice.setBillingState(value(identity, Field.STATE));
    invoice.setBillingPostalCode(value(identity, Field.POSTAL));
    invoice.setBillingCountry(
        identity != null && hasText(identity.country())
            ? identity.country().trim()
            : company.getCountry());
    invoice.setTaxIdSnapshot(value(identity, Field.TAX_ID));
    invoice.setTaxRegistrationSnapshot(value(identity, Field.TAX_REGISTRATION));
    invoice.setServicePeriodStart(period.getStartAt());
    invoice.setServicePeriodEnd(period.getEndAt());
    return invoiceRepository.saveAndFlush(invoice);
  }

  private void ensurePlatformLine(Invoice invoice, BillingPeriod period) {
    String key = "PLATFORM_PERIOD:" + period.getId();
    if (lineRepository.findByInvoiceIdAndIdempotencyKey(invoice.getId(), key).isPresent()) {
      return;
    }

    InvoiceLineItem line = new InvoiceLineItem();
    line.setInvoice(invoice);
    line.setLineType(InvoiceLineType.PLATFORM_PLAN);
    line.setDescription(
        "Platform subscription period "
            + period.getStartAt()
            + " to "
            + period.getEndAt());
    line.setQuantity(BigDecimal.ONE);
    line.setUnitAmount(period.getPlatformAmountSnapshot());
    line.setAmount(period.getPlatformAmountSnapshot().setScale(6, RoundingMode.HALF_UP));
    line.setCurrency(period.getCurrencySnapshot());
    line.setServicePeriodStart(period.getStartAt());
    line.setServicePeriodEnd(period.getEndAt());
    line.setSourceReference(
        period.getSubscription() == null
            ? "period:" + period.getId()
            : "subscription:" + period.getSubscription().getId());
    line.setIdempotencyKey(key);
    lineRepository.saveAndFlush(line);
  }

  private boolean hasPlatformSnapshot(BillingPeriod period) {
    return period.getSubscription() != null
        && period.getPlan() != null
        && period.getPlatformAmountSnapshot() != null
        && R1_CURRENCY.equalsIgnoreCase(period.getCurrencySnapshot());
  }

  private Invoice finalizeLocked(Invoice invoice, Instant finalizedAt) {
    BigDecimal subtotal = BigDecimal.ZERO;
    BigDecimal discount = BigDecimal.ZERO;
    BigDecimal credit = BigDecimal.ZERO;
    BigDecimal adjustment = BigDecimal.ZERO;
    BigDecimal tax = BigDecimal.ZERO;
    for (InvoiceLineItem line : lineRepository.findByInvoiceIdOrderByCreatedAtAsc(invoice.getId())) {
      switch (line.getLineType()) {
        case PLATFORM_PLAN, MANAGED_USAGE, PRIOR_PERIOD_USAGE_ADJUSTMENT ->
            subtotal = subtotal.add(line.getAmount());
        case DISCOUNT -> discount = discount.add(line.getAmount().abs());
        case CREDIT -> credit = credit.add(line.getAmount().abs());
        case MANUAL_ADJUSTMENT -> adjustment = adjustment.add(line.getAmount());
        case TAX -> tax = tax.add(line.getAmount());
      }
    }
    BigDecimal total = subtotal.subtract(discount).subtract(credit).add(adjustment).add(tax);
    BigDecimal amountDue = total.max(BigDecimal.ZERO);

    invoice.setSubtotal(money(subtotal));
    invoice.setDiscountAmount(money(discount));
    invoice.setCreditAmount(money(credit));
    invoice.setAdjustmentAmount(money(adjustment));
    invoice.setTaxAmount(money(tax));
    invoice.setTotal(money(total));
    invoice.setAmountDue(money(amountDue));
    invoice.setStatus(InvoiceStatus.OPEN);
    invoice.setFinalizedAt(finalizedAt);
    invoice.setDueAt(finalizedAt);
    return invoiceRepository.saveAndFlush(invoice);
  }

  private Invoice requireDraftForUpdate(Long invoiceId) {
    Invoice invoice =
        invoiceRepository
            .findByIdForUpdate(invoiceId)
            .orElseThrow(() -> new NotFoundException("Invoice not found"));
    if (invoice.getStatus() != InvoiceStatus.DRAFT) {
      throw new ConflictException("Finalized invoice snapshots are immutable");
    }
    return invoice;
  }

  private void assertSameLine(InvoiceLineItem existing, InvoiceLineRequest request) {
    if (existing.getLineType() != request.lineType()
        || !existing.getDescription().equals(request.description().trim())
        || existing.getQuantity().compareTo(request.quantity()) != 0
        || existing.getUnitAmount().compareTo(request.unitAmount()) != 0
        || !Objects.equals(existing.getServicePeriodStart(), request.servicePeriodStart())
        || !Objects.equals(existing.getServicePeriodEnd(), request.servicePeriodEnd())
        || !Objects.equals(
            existing.getOriginalServicePeriodStart(), request.originalServicePeriodStart())
        || !Objects.equals(
            existing.getOriginalServicePeriodEnd(), request.originalServicePeriodEnd())
        || !Objects.equals(existing.getSourceReference(), normalize(request.sourceReference()))) {
      throw new ConflictException(
          "Invoice line idempotency key was used for a materially different line");
    }
  }

  private void validateServiceRange(Instant start, Instant end) {
    if ((start == null) != (end == null)) {
      throw new BadRequestException("Service-period start and end must be provided together");
    }
    if (start != null && !end.isAfter(start)) {
      throw new BadRequestException("Service periods use half-open [start,end) ranges");
    }
  }

  private BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String normalizeRequired(String value, String field) {
    if (!hasText(value)) {
      throw new BadRequestException(field + " is required");
    }
    return value.trim();
  }

  private String normalize(String value) {
    return hasText(value) ? value.trim() : null;
  }

  private String value(InvoiceIdentitySnapshot identity, Field field) {
    if (identity == null) return null;
    return normalize(
        switch (field) {
          case ADDRESS1 -> identity.addressLine1();
          case ADDRESS2 -> identity.addressLine2();
          case CITY -> identity.city();
          case STATE -> identity.state();
          case POSTAL -> identity.postalCode();
          case TAX_ID -> identity.taxId();
          case TAX_REGISTRATION -> identity.taxRegistration();
        });
  }

  private InvoiceResponse toResponse(Invoice invoice) {
    return new InvoiceResponse(
        invoice.getId().toString(),
        invoice.getCompany().getId().toString(),
        invoice.getBillingPeriod() == null ? null : invoice.getBillingPeriod().getId().toString(),
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

  private enum Field {
    ADDRESS1,
    ADDRESS2,
    CITY,
    STATE,
    POSTAL,
    TAX_ID,
    TAX_REGISTRATION
  }
}
