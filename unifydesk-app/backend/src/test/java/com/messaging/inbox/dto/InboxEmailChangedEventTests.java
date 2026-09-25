package com.messaging.inbox.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.messaging.channel.enums.ProviderType;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class InboxEmailChangedEventTests {

  @Test
  void keepsSmallRealtimeChangesAsDeltas() {
    InboxEmailChangedEvent event =
        InboxEmailChangedEvent.boundedDelta(
            10L,
            ProviderType.GMAIL,
            List.of(20L),
            List.of(30L),
            List.of(40L));

    assertThat(event.fullRefreshRequired()).isFalse();
    assertThat(event.connectionId()).isEqualTo("10");
    assertThat(event.conversationIds()).containsExactly("20");
    assertThat(event.messageIds()).containsExactly("30");
    assertThat(event.deletedMessageIds()).containsExactly("40");
  }

  @Test
  void fallsBackToOneFullRefreshForOversizedChanges() {
    List<Long> messageIds = LongStream.rangeClosed(1, 101).boxed().toList();

    InboxEmailChangedEvent event =
        InboxEmailChangedEvent.boundedDelta(
            10L,
            ProviderType.MICROSOFT,
            List.of(),
            messageIds,
            List.of());

    assertThat(event.fullRefreshRequired()).isTrue();
    assertThat(event.conversationIds()).isEmpty();
    assertThat(event.messageIds()).isEmpty();
    assertThat(event.deletedMessageIds()).isEmpty();
  }
}
