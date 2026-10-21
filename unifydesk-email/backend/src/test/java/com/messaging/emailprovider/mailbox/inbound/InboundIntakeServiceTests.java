package com.messaging.emailprovider.mailbox.inbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.messaging.emailprovider.common.exception.BadRequestException;
import com.messaging.emailprovider.common.exception.NotFoundException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class InboundIntakeServiceTests {
  @Test
  void parsesRecipientFromEnvelopeNotMessageHeaders() {
    InboundIntakeService.Recipient target =
        InboundIntakeService.parseRecipient("Sales@Example.COM");
    assertEquals("sales", target.localPart());
    assertEquals("example.com", target.domain());
  }

  @Test
  void rejectsInvalidRecipientValues() {
    for (String address :
        new String[] {
          "",
          "abc",
          "@example.com",
          "x@@example.com",
          "x@example.com\nBCC:victim@example.com",
          "../x@example.com",
          "x@localhost",
          "x@bad..example.com"
        }) {
      assertThrows(
          BadRequestException.class, () -> InboundIntakeService.parseRecipient(address), address);
    }
  }

  @Test
  void rejectsDisabledGatewayBeforeReadingOrStoringUntrustedMime() {
    AtomicReference<String> sqlSeen = new AtomicReference<>();
    JdbcTemplate db =
        new JdbcTemplate() {
          @Override
          public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            sqlSeen.set(sql);
            return List.of();
          }
        };
    // No transaction template or spool is supplied: a disabled destination
    // must be rejected before any MIME I/O or quota-reservation side effect.
    InboundIntakeService intake = new InboundIntakeService(db, null, null, true);
    assertThrows(
        NotFoundException.class,
        () ->
            intake.accept(
                "owner@example.com",
                "mx-queue-abcdef",
                new ByteArrayInputStream(
                    "Subject: test\\r\\n\\r\\nbody".getBytes(StandardCharsets.US_ASCII))));
    assertTrue(sqlSeen.get().contains("d.inbound_enabled=TRUE"));
    assertTrue(sqlSeen.get().contains("m.status='ACTIVE'"));
    assertTrue(sqlSeen.get().contains("o.status='ACTIVE'"));
  }
}
