package com.messaging.channel.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GmailRealtimeStateServiceTests {

  @Test
  void comparesLargeHistoryIdsNumerically() {
    assertThat(
            GmailRealtimeStateService.compareHistoryId(
                "100000000000000000000", "99999999999999999999"))
        .isPositive();
    assertThat(GmailRealtimeStateService.compareHistoryId("42", "42")).isZero();
    assertThat(GmailRealtimeStateService.compareHistoryId("41", "42")).isNegative();
  }
}
