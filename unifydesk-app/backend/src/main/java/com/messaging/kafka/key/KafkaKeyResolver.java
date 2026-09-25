package com.messaging.kafka.key;

import com.messaging.kafka.event.KafkaEvent;
import org.springframework.stereotype.Component;

@Component
public class KafkaKeyResolver {

  public String resolve(KafkaEvent<?> event) {
    if (event.companyId() != null && event.userId() != null) {
      return event.companyId() + ":user:" + event.userId();
    }
    if (event.userId() != null) {
      return "user:" + event.userId();
    }
    if (event.companyId() != null) {
      return "company:" + event.companyId();
    }
    return event.correlationId() != null ? "correlation:" + event.correlationId() : event.eventId();
  }
}
