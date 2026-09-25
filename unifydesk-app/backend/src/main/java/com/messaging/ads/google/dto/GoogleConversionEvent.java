package com.messaging.ads.google.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record GoogleConversionEvent(
    Instant eventTimestamp,
    String transactionId,
    String eventSource,
    BigDecimal conversionValue,
    String currency,
    String gclid,
    String gbraid,
    String wbraid,
    List<String> emails,
    List<String> phoneNumbers,
    String userId) {}
