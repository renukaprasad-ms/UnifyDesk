package com.messaging.kafka.retry;

import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.kafka.exception.NonRetryableKafkaException;
import com.messaging.kafka.exception.RetryableKafkaException;
import org.springframework.stereotype.Component;

@Component
public class KafkaExceptionClassifier {

  public boolean isRetryable(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof NonRetryableKafkaException || current instanceof BadRequestException) {
        return false;
      }
      if (current instanceof RetryableKafkaException || current instanceof ServiceUnavailableException) {
        return true;
      }
      current = current.getCause();
    }
    return true;
  }
}
