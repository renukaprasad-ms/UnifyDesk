package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.messaging.realtime.service.RealtimeService;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetaWhatsAppRealtimePublisherTests {

  @Test
  void realtimeFailureIsAdvisoryAndDoesNotEscapeDurableFlow() {
    RealtimeService realtimeService = mock(RealtimeService.class);
    doThrow(new RuntimeException("realtime unavailable"))
        .when(realtimeService)
        .sendToCompany(anyLong(), any(), any());
    MetaWhatsAppRealtimePublisher publisher =
        new MetaWhatsAppRealtimePublisher(realtimeService);

    assertThatCode(() -> publisher.inboxChanged(10L, 20L, List.of(30L), List.of(40L)))
        .doesNotThrowAnyException();
    assertThatCode(() -> publisher.statusChanged(10L, 20L, 30L, 40L))
        .doesNotThrowAnyException();
  }
}
