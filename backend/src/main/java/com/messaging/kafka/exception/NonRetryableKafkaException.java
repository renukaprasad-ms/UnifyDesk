package com.messaging.kafka.exception;

public class NonRetryableKafkaException extends RuntimeException {

  public NonRetryableKafkaException(String message) {
    super(message);
  }

  public NonRetryableKafkaException(String message, Throwable cause) {
    super(message, cause);
  }
}
