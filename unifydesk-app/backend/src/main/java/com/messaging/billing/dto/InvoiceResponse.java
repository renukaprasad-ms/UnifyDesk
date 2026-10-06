package com.messaging.billing.dto;

import com.messaging.billing.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceResponse(
    String invoiceId,
    String companyId,
    String billingPeriodId,
    String invoiceNumber,
    InvoiceStatus status,
    String currency,
    BigDecimal subtotal,
    BigDecimal discountAmount,
    BigDecimal creditAmount,
    BigDecimal adjustmentAmount,
    BigDecimal taxAmount,
    BigDecimal total,
    BigDecimal amountDue,
    Instant servicePeriodStart,
    Instant servicePeriodEnd,
    Instant finalizedAt,
    Instant dueAt,
    Instant paidAt) {}
