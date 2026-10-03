package com.messaging.contacts.consent;

import com.messaging.contacts.consent.ConsentTypes.PreferenceCommand;
import com.messaging.contacts.consent.ConsentTypes.PreferenceSnapshot;
import com.messaging.contacts.consent.ConsentTypes.PreferenceState;
import java.time.Instant;

public final class ContactPreferenceProjectionPolicy {

  private ContactPreferenceProjectionPolicy() {}

  public static boolean shouldReplace(PreferenceSnapshot current, PreferenceCommand incoming) {
    return shouldReplace(
        current.state(), current.occurredAt(), incoming.state(), incoming.occurredAt());
  }

  public static boolean shouldReplace(
      PreferenceState currentState,
      Instant currentOccurredAt,
      PreferenceState incomingState,
      Instant incomingOccurredAt) {
    int timeComparison = incomingOccurredAt.compareTo(currentOccurredAt);
    if (timeComparison > 0) return true;
    if (timeComparison < 0) return false;
    return safetyRank(incomingState) > safetyRank(currentState);
  }

  private static int safetyRank(PreferenceState state) {
    return switch (state) {
      case OPTED_OUT -> 3;
      case UNKNOWN -> 2;
      case OPTED_IN -> 1;
    };
  }
}
