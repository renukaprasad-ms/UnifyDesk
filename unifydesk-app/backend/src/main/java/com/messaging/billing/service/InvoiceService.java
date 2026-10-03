package com.messaging.billing.service;

import com.messaging.billing.dto.InvoiceIdentitySnapshot;
import com.messaging.billing.dto.InvoiceLineRequest;
import com.messaging.billing.dto.InvoiceResponse;
import com.messaging.billing.entity.BillingPeriod;
import com.messaging.billing.entity.Invoice;
import com.messaging.billing.entity.InvoiceLineItem;
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
import com.messaging.company.repository.CompanyRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceService {

  private static final String R1_CURRENCY = "INR";

  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineItemRepository lineRepository;
  private final BillingPeriodRepository periodRepository;
  private final BillingPeriodService periodService;
  private final CompanyRepository companyRepository;

  @Transactional
  public InvoiceResponse createDraft(
      Long companyId, Long billingPeriodId, InvoiceIdentitySnapshot identity) {
    Company company =
        companyRepository
            .findById(companyId)
            .orElseThrow(() -> new NotFoundException("Company not found"));
    BillingPeriod period =
        periodRepository
            .findById(billingPeriodId)
            .orElseThrow(() -> new NotFoundException("Billing period not found"));
    if (!period.getCompany().getId().equals(companyId)) {
      throw new BadRequestException("Billing period does not belong to the company");
    }
    var existing = invoiceRepository.findByBillingPeriodId(billingPeriodId);
    if (existing.isPresent()) {
      return toResponse(existing.get());
    }

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
    return toResponse(invoiceRepository.saveAndFlush(invoice));
  }

  @Transactional
  public InvoiceResponse addLine(Long invoiceId, InvoiceLineRequest request) {
    Invoice invoice = requireDraft(invoiceId);
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
    lineRepository.saveAndFlush(line);
    return toResponse(invoice);
  }

  @Transactional
  public InvoiceResponse finalizeInvoice(Long invoiceId, Instant dueAt) {
    Invoice invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new NotFoundException("Invoice not found"));
    if (invoice.getStatus() != InvoiceStatus.DRAFT) {
      return toResponse(invoice);
    }

    BigDecimal subtotal = BigDecimal.ZERO;
    BigDecimal discount = BigDecimal.ZERO;
    BigDecimal credit = BigDecimal.ZERO;
    BigDecimal adjustment = BigDecimal.ZERO;
    BigDecimal tax = BigDecimal.ZERO;
    for (InvoiceLineItem line : lineRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId)) {
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
    invoice.setFinalizedAt(Instant.now());
    invoice.setDueAt(dueAt);
    return toResponse(invoiceRepository.saveAndFlush(invoice));
  }

  /**
   * B2 may call this only after provider/payment truth confirms collection.
   */
  @Transactional
  public InvoiceResponse markPaidConfirmed(Long invoiceId, Instant paidAt) {
    Invoice invoice =
        invoiceRepository
            .findById(invoiceId)
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
  public int finalizeReadyClosingPeriods(Instant now) {
    int finalized = 0;
    for (Invoice invoice : invoiceRepository.findByStatus(InvoiceStatus.DRAFT)) {
      BillingPeriod period = invoice.getBillingPeriod();
      if (period == null || period.getStatus() != BillingPeriodStatus.CLOSING) {
        continue;
      }
      finalizeInvoice(invoice.getId(), now);
      periodService.finalizePeriod(period.getId(), now);
      finalized++;
    }
    return finalized;
  }

  @Transactional(readOnly = true)
  public List<InvoiceResponse> invoices(Long companyId) {
    return invoiceRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
        .map(this::toResponse)
        .toList();
  }

  private Invoice requireDraft(Long invoiceId) {
    Invoice invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new NotFoundException("Invoice not found"));
    if (invoice.getStatus() != InvoiceStatus.DRAFT) {
      throw new ConflictException("Finalized invoice snapshots are immutable");
    }
    return invoice;
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
