package com.messaging.channel.meta.runtime;

public class MetaWhatsAppProviderException extends RuntimeException {
  private final Integer httpStatus;
  private final Integer metaCode;
  private final Integer metaSubcode;
  private final String fbtraceId;
  private final String safeCategory;
  private final boolean retryable;
  private final boolean ambiguousOutcome;

  public MetaWhatsAppProviderException(
      Integer httpStatus,
      Integer metaCode,
      Integer metaSubcode,
      String fbtraceId,
      String safeCategory,
      boolean retryable,
      boolean ambiguousOutcome,
      Throwable cause) {
    super(safeCategory, cause);
    this.httpStatus = httpStatus;
    this.metaCode = metaCode;
    this.metaSubcode = metaSubcode;
    this.fbtraceId = fbtraceId;
    this.safeCategory = safeCategory;
    this.retryable = retryable;
    this.ambiguousOutcome = ambiguousOutcome;
  }

  public Integer httpStatus() { return httpStatus; }
  public Integer metaCode() { return metaCode; }
  public Integer metaSubcode() { return metaSubcode; }
  public String fbtraceId() { return fbtraceId; }
  public String safeCategory() { return safeCategory; }
  public boolean retryable() { return retryable; }
  public boolean ambiguousOutcome() { return ambiguousOutcome; }
}
