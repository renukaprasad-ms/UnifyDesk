package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MetaWhatsAppWebhookEventStoreTests {

  @Test
  void duplicateDurableInsertIsNoOpAndStillSuccessful() {
    AtomicInteger updates = new AtomicInteger();
    JdbcTemplate jdbcTemplate =
        new JdbcTemplate() {
          @Override
          public int update(String sql, Object... args) {
            assertThat(sql).contains("on conflict (event_key) do nothing");
            return updates.getAndIncrement() == 0 ? 1 : 0;
          }
        };
    MetaWhatsAppWebhookEventStore store =
        new MetaWhatsAppWebhookEventStore(
            jdbcTemplate, new MetaWhatsAppRuntimeProperties());
    var event =
        new MetaWhatsAppWebhookNormalizer.NormalizedEvent(
            "msg:phone-1:wamid.1",
            "waba-1",
            "phone-1",
            "wamid.1",
            "MESSAGE",
            Instant.parse("2026-09-29T00:00:00Z"),
            "{\"message\":{\"id\":\"wamid.1\"}}");

    assertThat(store.persist(List.of(event))).isEqualTo(1);
    assertThat(store.persist(List.of(event))).isZero();
  }
}
