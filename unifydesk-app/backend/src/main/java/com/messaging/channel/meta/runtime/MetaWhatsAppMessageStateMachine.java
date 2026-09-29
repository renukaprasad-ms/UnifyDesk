package com.messaging.channel.meta.runtime;

import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class MetaWhatsAppMessageStateMachine {

  public State providerState(String provider) {
    if (provider == null) return new State("UNKNOWN", 5);
    return switch (provider.toLowerCase(java.util.Locale.ROOT)) {
      case "sent" -> new State("SENT", 20);
      case "delivered" -> new State("DELIVERED", 30);
      case "read" -> new State("READ", 40);
      case "failed" -> new State("FAILED", 25);
      default -> new State("UNKNOWN", 5);
    };
  }

  public boolean shouldApply(
      String currentStatus,
      int currentRank,
      Instant currentProviderAt,
      State next,
      Instant incomingProviderAt) {
    if (next.rank() < currentRank) return false;
    if (next.rank() == currentRank
        && currentProviderAt != null
        && !incomingProviderAt.isAfter(currentProviderAt)) return false;
    return !"FAILED".equals(next.status())
        || (!"DELIVERED".equals(currentStatus) && !"READ".equals(currentStatus));
  }

  public record State(String status, int rank) {}
}
