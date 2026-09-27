package com.messaging.notification.platform.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationCursorCodec {

  private final ObjectMapper objectMapper;

  public String encode(Long companyId, boolean unreadOnly, Instant lastEventAt, long id) {
    try {
      Cursor cursor =
          new Cursor(
              1,
              companyId == null ? null : Long.toString(companyId),
              unreadOnly,
              lastEventAt.toString(),
              Long.toString(id));
      byte[] json = objectMapper.writeValueAsBytes(cursor);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to encode notification cursor", exception);
    }
  }

  public Decoded decode(String value, Long companyId, boolean unreadOnly) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      byte[] json = Base64.getUrlDecoder().decode(value.getBytes(StandardCharsets.US_ASCII));
      Cursor cursor = objectMapper.readValue(json, Cursor.class);
      String expectedCompany = companyId == null ? null : Long.toString(companyId);
      if (cursor.v() != 1
          || cursor.unreadOnly() != unreadOnly
          || !java.util.Objects.equals(cursor.companyId(), expectedCompany)) {
        throw new BadRequestException("Invalid notification cursor");
      }
      return new Decoded(Instant.parse(cursor.lastEventAt()), Long.parseLong(cursor.id()));
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BadRequestException("Invalid notification cursor");
    }
  }

  private record Cursor(
      int v, String companyId, boolean unreadOnly, String lastEventAt, String id) {}

  public record Decoded(Instant lastEventAt, long id) {}
}
