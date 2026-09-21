package com.messaging.channel.imapsmtp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImapSmtpCursorCodec {

  private final ObjectMapper objectMapper;

  public String encode(ImapSmtpCursorState state) {
    try {
      return objectMapper.writeValueAsString(state);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize IMAP sync cursor", exception);
    }
  }

  public ImapSmtpCursorState decode(String value) {
    if (value == null || value.isBlank()) {
      return ImapSmtpCursorState.empty();
    }
    try {
      ImapSmtpCursorState decoded = objectMapper.readValue(value, ImapSmtpCursorState.class);
      return decoded == null ? ImapSmtpCursorState.empty() : decoded;
    } catch (JsonProcessingException exception) {
      return ImapSmtpCursorState.empty();
    }
  }
}
