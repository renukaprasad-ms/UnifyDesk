package com.messaging.kafka.retry;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.kafka.exception.NonRetryableKafkaException;
import com.messaging.kafka.exception.RetryableKafkaException;
import org.junit.jupiter.api.Test;

class KafkaExceptionClassifierTests {

  private final KafkaExceptionClassifier classifier = new KafkaExceptionClassifier();

  @Test
  void classifiesRetryableFailures() {
    assertThat(classifier.isRetryable(new RetryableKafkaException("timeout"))).isTrue();
    assertThat(classifier.isRetryable(new ServiceUnavailableException("smtp down"))).isTrue();
  }

  @Test
  void classifiesNonRetryableFailures() {
    assertThat(classifier.isRetryable(new NonRetryableKafkaException("bad payload"))).isFalse();
    assertThat(classifier.isRetryable(new BadRequestException("bad request"))).isFalse();
  }
}
