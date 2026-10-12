package com.messaging.integrations.unifydeskemail;

import java.io.IOException;

/** Explicit HTTP rejection is distinct from uncertain remote acceptance. */
public class UnifyDeskEmailHttpException extends IOException {
  private final int statusCode;

  public UnifyDeskEmailHttpException(int statusCode) {
    super("UnifyDesk Email API rejected request with HTTP " + statusCode);
    this.statusCode = statusCode;
  }

  public int statusCode() { return statusCode; }
}
