package com.messaging.channel.exception;

import com.messaging.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class PlanLimitExceededException extends ApiException {

  public PlanLimitExceededException(String message) {
    super(HttpStatus.FORBIDDEN, message);
  }
}
