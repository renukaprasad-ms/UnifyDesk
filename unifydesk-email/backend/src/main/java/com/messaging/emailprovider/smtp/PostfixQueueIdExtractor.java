package com.messaging.emailprovider.smtp;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PostfixQueueIdExtractor {

  private static final Pattern QUEUED_AS = Pattern.compile("(?i)\\bqueued\\s+as\\s+([A-Z0-9]+)\\b");

  public Optional<String> extract(String response) {
    if (response == null || response.isBlank()) {
      return Optional.empty();
    }
    Matcher matcher = QUEUED_AS.matcher(response);
    return matcher.find() ? Optional.of(matcher.group(1).toUpperCase()) : Optional.empty();
  }
}
