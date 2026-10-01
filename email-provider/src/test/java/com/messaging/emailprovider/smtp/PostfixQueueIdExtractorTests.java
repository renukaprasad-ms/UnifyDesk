package com.messaging.emailprovider.smtp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PostfixQueueIdExtractorTests {

  private final PostfixQueueIdExtractor extractor = new PostfixQueueIdExtractor();

  @Test
  void extractsQueueIdFromPostfixResponse() {
    assertEquals(
        "4F9A812345", extractor.extract("250 2.0.0 Ok: queued as 4F9A812345").orElseThrow());
  }

  @Test
  void returnsEmptyWhenQueueIdIsMissing() {
    assertTrue(extractor.extract("250 2.0.0 queued").isEmpty());
  }
}
