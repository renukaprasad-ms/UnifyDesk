package com.messaging.media.exception;

import com.messaging.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class MediaException extends ApiException {

  public MediaException(HttpStatus status, String code) {
    super(status, code);
  }

  public MediaException(HttpStatus status, String code, Throwable cause) {
    super(status, code, cause);
  }
}
