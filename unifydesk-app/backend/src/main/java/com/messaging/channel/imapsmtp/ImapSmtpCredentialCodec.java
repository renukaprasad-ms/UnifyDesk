package com.messaging.channel.imapsmtp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImapSmtpCredentialCodec {

  private final ObjectMapper objectMapper;

  public String encode(ImapSmtpStoredCredentials credentials) {
    try {
      return objectMapper.writeValueAsString(credentials);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize IMAP/SMTP credentials", exception);
    }
  }

  public ImapSmtpStoredCredentials decode(String value) {
    try {
      return objectMapper.readValue(value, ImapSmtpStoredCredentials.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to read IMAP/SMTP credentials", exception);
    }
  }
}
