package com.messaging.emailprovider;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest
class EmailProviderApplicationTests {

  @Autowired private KafkaTemplate<String, String> kafkaTemplate;

  @Test
  void contextLoads() {}

  @Test
  void kafkaTemplateIsAutoConfigured() {
    // The bean must exist even if the test has no running Kafka broker.
    assertNotNull(kafkaTemplate);
  }
}
