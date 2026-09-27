package com.messaging.managedsms.events;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ManagedSmsEventInboxServiceTests {

  @Test
  void forgedWebhookIsRejectedBeforeCorrelationOrMutation() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ManagedSmsRuntimeRepository runtime = mock(ManagedSmsRuntimeRepository.class);
    ManagedSmsRuntimeProperties properties = properties();
    ManagedSmsEventInboxService service =
        new ManagedSmsEventInboxService(jdbc, new ObjectMapper(), runtime, properties);

    byte[] body = "{\"CRQID\":\"abc123\",\"requestId\":\"req-1\",\"status\":\"1\"}"
        .getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> service.receive(body, "wrong"))
        .isInstanceOf(BadRequestException.class);

    verifyNoInteractions(runtime, jdbc);
  }

  @Test
  void crossRequestReplayIsRecordedRejectedAndNeverQueued() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    ManagedSmsRuntimeRepository runtime = mock(ManagedSmsRuntimeRepository.class);
    when(runtime.findCorrelation("abc123"))
        .thenReturn(
            Optional.of(
                new ManagedSmsRuntimeRepository.CorrelationRuntime(
                    "abc123", 10L, 60L, 70L, 1, "req-original", "client-a")));
    ManagedSmsEventInboxService service =
        new ManagedSmsEventInboxService(jdbc, new ObjectMapper(), runtime, properties());

    byte[] body =
        "{\"CRQID\":\"abc123\",\"requestId\":\"req-other\",\"status\":\"1\"}"
            .getBytes(StandardCharsets.UTF_8);
    service.receive(body, "webhook-secret");

    verify(runtime).findCorrelation("abc123");
    verify(jdbc).update(anyString(), any(Object[].class));
  }

  private ManagedSmsRuntimeProperties properties() {
    ManagedSmsRuntimeProperties properties = new ManagedSmsRuntimeProperties();
    properties.setWebhookSecret("webhook-secret");
    properties.setWebhookMaxBytes(8192);
    return properties;
  }
}
