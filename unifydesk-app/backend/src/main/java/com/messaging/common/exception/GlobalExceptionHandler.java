package com.messaging.common.exception;

import com.messaging.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
    HttpStatus status = exception.getStatus();
    return ResponseEntity.status(status)
        .body(ApiResponse.error(status.value(), exception.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception) {
    String message =
        exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(
                error -> {
                  String defaultMessage = error.getDefaultMessage();
                  if ("containsEuPoliticalAdvertising".equals(error.getField())
                      && defaultMessage != null
                      && !defaultMessage.isBlank()) {
                    return defaultMessage;
                  }
                  return error.getField() + ": " + defaultMessage;
                })
            .orElse("Invalid request");

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), message));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
      ConstraintViolationException exception) {
    String message =
        exception.getConstraintViolations().stream()
            .findFirst()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .orElse("Invalid request");

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), message));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
      NoResourceFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), "Resource not found"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
    log.error("Unhandled request exception", exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Something went wrong"));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnreadableRequest() {
    return ResponseEntity.badRequest().body(ApiResponse.error(400, "Invalid request body"));
  }
}
