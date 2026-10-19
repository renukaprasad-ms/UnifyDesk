package com.messaging.billing.service;

import com.messaging.billing.dto.InvoiceResponse;
import com.messaging.billing.entity.Invoice;
import com.messaging.billing.entity.InvoiceLineItem;
import com.messaging.billing.entity.SubscriptionChange;
import com.messaging.billing.enums.InvoiceLineType;
import com.messaging.billing.enums.InvoiceStatus;
import com.messaging.billing.enums.SubscriptionChangeStatus;
import com.messaging.billing.enums.SubscriptionChangeType;
import com.messaging.billing.repository.InvoiceLineItemRepository;
import com.messaging.billing.repository.InvoiceRepository;
import com.messaging.billing.repository.SubscriptionChangeRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * B1-owned financial boundary for immediate paid upgrades.
 *
 * <p>B2 must resolve this invoice from the immutable {@link SubscriptionChange} snapshot and
 * collect the finalized invoice amount. B2 must not recalculate proration or construct arbitrary
 * upgrade lines.
 */
@Service
@RequiredArgsConstructor
public class UpgradeInvoiceService {

  private static final String R1_CURRENCY = "INR";

  private final SubscriptionChangeRepository changeRepository;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineItemRepository lineRepository;
  private final InvoiceService invoiceService;

  /**
   * Creates or resolves exactly one canonical invoice for a positive-due upgrade change.
   *
   * <p>The quote reference is an idempotent command guard only. Financial values are always read
   * from the locked immutable SubscriptionChange snapshot.
   */
  @Transactional
  public InvoiceResponse createOrResolveUpgradeInvoice(
      Long companyId, Long changeId, String quoteReference) {
    SubscriptionChange change =
        changeRepository
            .findByIdForUpdate(changeId)
            .orElseThrow(() -> new NotFoundException("Subscription change not found"));
    assertInvoiceableChange(companyId, change, quoteReference);

    var existing = invoiceRepository.findBySubscriptionChangeId(changeId);
    if (existing.isPresent()) {
      assertCanonicalSnapshot(existing.get(), change);
      bindChangeInvoice(change, existing.get());
      return toResponse(existing.get());
    }

    Invoice invoice = new Invoice();
    invoice.setCompany(change.getCompany());
    invoice.setSubscriptionChange(change);
    invoice.setSourceReference(change.getQuoteReference());
    invoice.setCurrency(R1_CURRENCY);
    invoice.setStatus(InvoiceStatus.DRAFT);
    invoice.setCompanyNameSnapshot(change.getCompany().getName());
    invoice.setBusinessEmailSnapshot(change.getCompany().getBusinessEmail());
    invoice.setBillingCountry(change.getCompany().getCountry());
    invoice.setServicePeriodStart(change.getCurrentPeriodStart());
    invoice.setServicePeriodEnd(change.getCurrentPeriodEnd());
    invoice = invoiceRepository.saveAndFlush(invoice);

    saveLine(
        invoice,
        InvoiceLineType.PLATFORM_UPGRADE,
        "Immediate platform upgrade to plan "
            + change.getToPlan().getId()
            + " / price "
            + change.getToPlanPrice().getId(),
        change.getTargetProratedCharge(),
        "UPGRADE_TARGET:" + change.getId(),
        "subscription-change:" + change.getId());
    saveLine(
        invoice,
        InvoiceLineType.CREDIT,
        "Unused current platform period credit",
        change.getUnusedCurrentCredit(),
        "UPGRADE_CREDIT:" + change.getId(),
        "subscription-change:" + change.getId());

    InvoiceResponse finalized =
        invoiceService.finalizeInvoice(invoice.getId(), change.getRequestedAt());
    if (finalized.amountDue() == null
        || finalized.amountDue().compareTo(change.getAmountDue()) != 0) {
      throw new ConflictException(
          "Canonical upgrade invoice does not match the immutable proration snapshot");
    }

    Invoice canonical =
        invoiceRepository
            .findByIdForUpdate(invoice.getId())
            .orElseThrow(
                () -> new NotFoundException("Upgrade invoice not found after finalization"));
    assertCanonicalSnapshot(canonical, change);
    bindChangeInvoice(change, canonical);
    return toResponse(canonical);
  }

  /**
   * B1 confirmation gate consumed by SubscriptionChangeService after B2 has recorded verified
   * payment truth. Merely possessing an invoice ID is insufficient.
   */
  @Transactional
  public void assertPaidCanonicalInvoice(SubscriptionChange change, Long invoiceId) {
    Invoice invoice =
        invoiceRepository
            .findByIdForUpdate(invoiceId)
            .orElseThrow(() -> new NotFoundException("Upgrade invoice not found"));
    assertCanonicalSnapshot(invoice, change);
    if (invoice.getStatus() != InvoiceStatus.PAID || invoice.getPaidAt() == null) {
      throw new ConflictException("Upgrade invoice has not crossed the confirmed payment boundary");
    }
  }

  private void bindChangeInvoice(SubscriptionChange change, Invoice invoice) {
    if (change.getInvoiceId() != null && !change.getInvoiceId().equals(invoice.getId())) {
      throw new ConflictException(
          "Subscription change is already bound to a different financial invoice");
    }
    if (change.getInvoiceId() == null) {
      change.setInvoiceId(invoice.getId());
      changeRepository.saveAndFlush(change);
    }
  }

  private void assertInvoiceableChange(
      Long companyId, SubscriptionChange change, String quoteReference) {
    if (!change.getCompany().getId().equals(companyId)) {
      throw new BadRequestException("Subscription change does not belong to the company");
    }
    if (change.getChangeType() != SubscriptionChangeType.UPGRADE) {
      throw new BadRequestException("Only upgrade changes have an immediate upgrade invoice");
    }
    if (change.getStatus() != SubscriptionChangeStatus.PENDING_PAYMENT
        && change.getStatus() != SubscriptionChangeStatus.APPLIED) {
      throw new ConflictException("Upgrade is not in an invoiceable state");
    }
    if (change.getAmountDue() == null || change.getAmountDue().signum() <= 0) {
      throw new BadRequestException("Zero-due upgrades do not require a paid invoice");
    }
    if (!R1_CURRENCY.equalsIgnoreCase(change.getCurrency())) {
      throw new BadRequestException("Billing R1 supports INR only");
    }
    if (quoteReference == null
        || change.getQuoteReference() == null
        || !change.getQuoteReference().equals(quoteReference.trim())) {
      throw new ConflictException(
          "Upgrade invoice replay does not match the immutable quote reference");
    }
    if (change.getCurrentPeriodStart() == null
        || change.getCurrentPeriodEnd() == null
        || !change.getCurrentPeriodEnd().isAfter(change.getCurrentPeriodStart())) {
      throw new ConflictException("Upgrade change has no valid current service-period snapshot");
    }
    if (change.getTargetProratedCharge() == null || change.getUnusedCurrentCredit() == null) {
      throw new ConflictException("Upgrade change has an incomplete proration snapshot");
    }
    BigDecimal reconstructed =
        change.getTargetProratedCharge().subtract(change.getUnusedCurrentCredit());
    if (reconstructed.compareTo(change.getAmountDue()) != 0) {
      throw new ConflictException("Upgrade proration snapshot is internally inconsistent");
    }
  }

  private void assertCanonicalSnapshot(Invoice invoice, SubscriptionChange change) {
    if (!invoice.getCompany().getId().equals(change.getCompany().getId())
        || invoice.getSubscriptionChange() == null
        || !invoice.getSubscriptionChange().getId().equals(change.getId())
        || !Objects.equals(invoice.getSourceReference(), change.getQuoteReference())
        || !R1_CURRENCY.equalsIgnoreCase(invoice.getCurrency())
        || !Objects.equals(invoice.getServicePeriodStart(), change.getCurrentPeriodStart())
        || !Objects.equals(invoice.getServicePeriodEnd(), change.getCurrentPeriodEnd())) {
      throw new ConflictException(
          "Existing upgrade invoice conflicts with the immutable subscription change");
    }

    if (invoice.getStatus() == InvoiceStatus.DRAFT) {
      throw new ConflictException("Canonical upgrade invoice is not finalized");
    }

    List<InvoiceLineItem> lines =
        lineRepository.findByInvoiceIdOrderByCreatedAtAsc(invoice.getId());
    if (lines.size() != 2) {
      throw new ConflictException("Canonical upgrade invoice line snapshot is inconsistent");
    }

    InvoiceLineItem target =
        lines.stream()
            .filter(line -> line.getLineType() == InvoiceLineType.PLATFORM_UPGRADE)
            .findFirst()
            .orElseThrow(() -> new ConflictException("Canonical upgrade target charge is missing"));
    InvoiceLineItem credit =
        lines.stream()
            .filter(line -> line.getLineType() == InvoiceLineType.CREDIT)
            .findFirst()
            .orElseThrow(
                () -> new ConflictException("Canonical upgrade credit snapshot is missing"));

    if (target.getAmount().compareTo(change.getTargetProratedCharge()) != 0
        || credit.getAmount().compareTo(change.getUnusedCurrentCredit()) != 0
        || !Objects.equals(target.getServicePeriodStart(), change.getCurrentPeriodStart())
        || !Objects.equals(target.getServicePeriodEnd(), change.getCurrentPeriodEnd())
        || !Objects.equals(credit.getServicePeriodStart(), change.getCurrentPeriodStart())
        || !Objects.equals(credit.getServicePeriodEnd(), change.getCurrentPeriodEnd())) {
      throw new ConflictException(
          "Canonical upgrade invoice financial snapshot does not match the change");
    }

    if (invoice.getStatus() == InvoiceStatus.PAID) {
      if (invoice.getTotal().compareTo(change.getAmountDue()) != 0) {
        throw new ConflictException("Paid upgrade invoice total conflicts with the change");
      }
    } else if (invoice.getAmountDue().compareTo(change.getAmountDue()) != 0) {
      throw new ConflictException("Upgrade invoice amount due conflicts with the change");
    }
  }

  private void saveLine(
      Invoice invoice,
      InvoiceLineType lineType,
      String description,
      BigDecimal amount,
      String idempotencyKey,
      String sourceReference) {
    InvoiceLineItem line = new InvoiceLineItem();
    line.setInvoice(invoice);
    line.setLineType(lineType);
    line.setDescription(description);
    line.setQuantity(BigDecimal.ONE);
    line.setUnitAmount(amount);
    line.setAmount(amount.setScale(6, RoundingMode.HALF_UP));
    line.setCurrency(R1_CURRENCY);
    line.setServicePeriodStart(invoice.getServicePeriodStart());
    line.setServicePeriodEnd(invoice.getServicePeriodEnd());
    line.setSourceReference(sourceReference);
    line.setIdempotencyKey(idempotencyKey);
    lineRepository.saveAndFlush(line);
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
}
