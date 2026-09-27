package com.messaging.notification.platform.core.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationCursorCodecTests {

  private final NotificationCursorCodec codec = new NotificationCursorCodec(new ObjectMapper());

  @Test
  void roundTripsSeekCursorWithoutNumericIdLoss() {
    Instant timestamp = Instant.parse("2026-09-27T06:30:00Z");
    long id = 922337203685470000L;

    String encoded = codec.encode(922337203685470100L, true, timestamp, id);
    NotificationCursorCodec.Decoded decoded =
        codec.decode(encoded, 922337203685470100L, true);

    assertThat(decoded.lastEventAt()).isEqualTo(timestamp);
    assertThat(decoded.id()).isEqualTo(id);
  }

  @Test
  void rejectsCursorReusedAcrossCompanyOrUnreadFilter() {
    String encoded =
        codec.encode(10L, false, Instant.parse("2026-09-27T06:30:00Z"), 20L);

    assertThatThrownBy(() -> codec.decode(encoded, 11L, false))
        .isInstanceOf(BadRequestException.class)
        .hasMessage("Invalid notification cursor");
    assertThatThrownBy(() -> codec.decode(encoded, 10L, true))
        .isInstanceOf(BadRequestException.class)
        .hasMessage("Invalid notification cursor");
  }

  @Test
  void rejectsMalformedCursor() {
    assertThatThrownBy(() -> codec.decode("not-base64!", null, false))
        .isInstanceOf(BadRequestException.class)
        .hasMessage("Invalid notification cursor");
  }
}
