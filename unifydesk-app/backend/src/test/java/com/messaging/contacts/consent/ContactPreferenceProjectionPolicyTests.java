package com.messaging.contacts.consent;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.contacts.consent.ConsentTypes.PreferenceState;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ContactPreferenceProjectionPolicyTests {

  private static final Instant T1 = Instant.parse("2026-10-02T10:00:00Z");
  private static final Instant T2 = Instant.parse("2026-10-02T10:01:00Z");

  @Test
  void newerOptOutReplacesOlderOptIn() {
    assertThat(
            ContactPreferenceProjectionPolicy.shouldReplace(
                PreferenceState.OPTED_IN, T1, PreferenceState.OPTED_OUT, T2))
        .isTrue();
  }

  @Test
  void staleOptInCannotReplaceNewerOptOut() {
    assertThat(
            ContactPreferenceProjectionPolicy.shouldReplace(
                PreferenceState.OPTED_OUT, T2, PreferenceState.OPTED_IN, T1))
        .isFalse();
  }

  @Test
  void strictlyLaterReconsentCanReplaceOptOut() {
    assertThat(
            ContactPreferenceProjectionPolicy.shouldReplace(
                PreferenceState.OPTED_OUT, T1, PreferenceState.OPTED_IN, T2))
        .isTrue();
  }

  @Test
  void equalTimeOptOutWinsAgainstOptIn() {
    assertThat(
            ContactPreferenceProjectionPolicy.shouldReplace(
                PreferenceState.OPTED_IN, T1, PreferenceState.OPTED_OUT, T1))
        .isTrue();
  }

  @Test
  void equalTimeOptInCannotReplaceOptOut() {
    assertThat(
            ContactPreferenceProjectionPolicy.shouldReplace(
                PreferenceState.OPTED_OUT, T1, PreferenceState.OPTED_IN, T1))
        .isFalse();
  }

  @Test
  void equalTimeUnknownCannotWeakenOptOut() {
    assertThat(
            ContactPreferenceProjectionPolicy.shouldReplace(
                PreferenceState.OPTED_OUT, T1, PreferenceState.UNKNOWN, T1))
        .isFalse();
  }
}
