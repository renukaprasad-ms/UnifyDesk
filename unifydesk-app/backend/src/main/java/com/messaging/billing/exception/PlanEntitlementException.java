package com.messaging.billing.exception;

import com.messaging.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class PlanEntitlementException extends ApiException {
  public PlanEntitlementException(String message) {
    super(HttpStatus.FORBIDDEN, message);
  }
}
