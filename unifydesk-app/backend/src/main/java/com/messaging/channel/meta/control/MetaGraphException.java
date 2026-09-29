package com.messaging.channel.meta.control;

public class MetaGraphException extends RuntimeException {

  private final String operation;
  private final int httpStatus;
  private final String metaType;
  private final Integer metaCode;
  private final Integer metaSubcode;
  private final String fbtraceId;
  private final SafeCategory safeCategory;
  private final boolean retryable;

  public MetaGraphException(
      String operation,
      int httpStatus,
      String metaType,
      Integer metaCode,
      Integer metaSubcode,
      String fbtraceId,
      SafeCategory safeCategory,
      boolean retryable) {
    super(
        "Meta Graph operation "
            + safe(operation)
            + " failed ("
            + safeCategory.name()
            + ", http="
            + httpStatus
            + ")");
    this.operation = safe(operation);
    this.httpStatus = httpStatus;
    this.metaType = bounded(metaType, 120);
    this.metaCode = metaCode;
    this.metaSubcode = metaSubcode;
    this.fbtraceId = bounded(fbtraceId, 255);
    this.safeCategory = safeCategory;
    this.retryable = retryable;
  }

  public String operation() {
    return operation;
  }

  public int httpStatus() {
    return httpStatus;
  }

  public String metaType() {
    return metaType;
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

  public SafeCategory safeCategory() {
    return safeCategory;
  }

  public boolean retryable() {
    return retryable;
  }

  public String safeCode() {
    if (metaCode != null) {
      return "META_" + safeCategory.name() + "_" + metaCode;
    }
    if (httpStatus > 0) {
      return "META_" + safeCategory.name() + "_HTTP_" + httpStatus;
    }
    return "META_" + safeCategory.name();
  }

  private static String safe(String value) {
    return value == null || value.isBlank() ? "unknown" : value;
  }

  private static String bounded(String value, int max) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
  }

  public enum SafeCategory {
    AUTH,
    PERMISSION,
    ASSET,
    RATE_LIMIT,
    TRANSIENT,
    INVALID_REQUEST,
    PROVIDER_ERROR
  }
}
