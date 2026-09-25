package com.messaging.kafka.exception;

public class RetryableKafkaException extends RuntimeException {

  public RetryableKafkaException(String message) {
    super(message);
  }

  public RetryableKafkaException(String message, Throwable cause) {
    super(message, cause);
  }
}
