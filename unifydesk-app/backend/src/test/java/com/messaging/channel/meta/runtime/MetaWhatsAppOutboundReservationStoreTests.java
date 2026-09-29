package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MetaWhatsAppOutboundReservationStoreTests {

  @Test
  void duplicateClientRequestIdDoesNotCreateSecondReservation() {
    AtomicInteger calls = new AtomicInteger();
    JdbcTemplate jdbcTemplate =
        new JdbcTemplate() {
          @Override
          public int update(String sql, Object... args) {
            assertThat(sql).contains("on conflict (company_id,connection_id,client_request_id)");
            return calls.getAndIncrement() == 0 ? 1 : 0;
          }
        };
    MetaWhatsAppOutboundReservationStore store =
        new MetaWhatsAppOutboundReservationStore(jdbcTemplate);
    Instant now = Instant.parse("2026-09-29T00:00:00Z");

    var first = store.tryReserve(10L, 20L, "request-1", "TEXT", now);
    var duplicate = store.tryReserve(10L, 20L, "request-1", "TEXT", now);

    assertThat(first.created()).isTrue();
    assertThat(duplicate.created()).isFalse();
  }
}
