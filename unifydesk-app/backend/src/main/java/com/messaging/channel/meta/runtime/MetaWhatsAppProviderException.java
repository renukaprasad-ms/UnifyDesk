package com.messaging.channel.meta.runtime;

import java.time.Duration;

public class MetaWhatsAppProviderException extends RuntimeException {
  private final Integer httpStatus;
  private final Integer metaCode;
  private final Integer metaSubcode;
  private final String fbtraceId;
  private final String safeCategory;
  private final boolean retryable;
  private final boolean ambiguousOutcome;
  private final Duration retryAfter;

  public MetaWhatsAppProviderException(
      Integer httpStatus,
      Integer metaCode,
      Integer metaSubcode,
      String fbtraceId,
      String safeCategory,
      boolean retryable,
      boolean ambiguousOutcome,
      Throwable cause) {
    this(
        httpStatus,
        metaCode,
        metaSubcode,
        fbtraceId,
        safeCategory,
        retryable,
        ambiguousOutcome,
        null,
        cause);
  }

  public MetaWhatsAppProviderException(
      Integer httpStatus,
      Integer metaCode,
      Integer metaSubcode,
      String fbtraceId,
      String safeCategory,
      boolean retryable,
      boolean ambiguousOutcome,
      Duration retryAfter,
      Throwable cause) {
    super(safeCategory, cause);
    this.httpStatus = httpStatus;
    this.metaCode = metaCode;
    this.metaSubcode = metaSubcode;
    this.fbtraceId = fbtraceId;
    this.safeCategory = safeCategory;
    this.retryable = retryable;
    this.ambiguousOutcome = ambiguousOutcome;
    this.retryAfter = retryAfter;
  }

  public Integer httpStatus() {
    return httpStatus;
  }

  public Integer metaCode() {
    return metaCode;
  }

  public Integer metaSubcode() {
    return metaSubcode;
  }

  public String fbtraceId() {
    return fbtraceId;
  }

  public String safeCategory() {
    return safeCategory;
  }

  public boolean retryable() {
    return retryable;
  }

  public boolean ambiguousOutcome() {
    return ambiguousOutcome;
  }

  public Duration retryAfter() {
    return retryAfter;
  }
}
