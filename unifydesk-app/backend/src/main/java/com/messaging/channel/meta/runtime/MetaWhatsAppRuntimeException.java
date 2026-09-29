package com.messaging.channel.meta.runtime;

public class MetaWhatsAppRuntimeException extends RuntimeException {
  private final String safeCode;
  private final boolean retryable;

  public MetaWhatsAppRuntimeException(String safeCode, boolean retryable, String message) {
    super(message);
    this.safeCode = safeCode;
    this.retryable = retryable;
  }

  public MetaWhatsAppRuntimeException(
      String safeCode, boolean retryable, String message, Throwable cause) {
    super(message, cause);
    this.safeCode = safeCode;
    this.retryable = retryable;
  }

  public String safeCode() {
    return safeCode;
  }

  public boolean retryable() {
    return retryable;
  }
}
