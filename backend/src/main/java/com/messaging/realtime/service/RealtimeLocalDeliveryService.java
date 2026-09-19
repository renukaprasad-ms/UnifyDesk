package com.messaging.realtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.realtime.dto.RealtimeDispatch;
import com.messaging.realtime.dto.RealtimeEnvelope;
import com.messaging.realtime.service.RealtimeConnectionRegistry.RealtimeConnection;
import java.io.IOException;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeLocalDeliveryService {

  private final RealtimeConnectionRegistry registry;
  private final ObjectMapper objectMapper;

  public void deliver(RealtimeDispatch dispatch) {
    if (dispatch == null || dispatch.target() == null || dispatch.messageJson() == null) {
      log.warn("WS_DIAG local_delivery_skip reason=invalid_dispatch");
      return;
    }

    log.debug(
        "WS_DIAG local_delivery target={} userId={} platform={} socketId={}",
        dispatch.target(),
        dispatch.userId(),
        dispatch.platform(),
        dispatch.socketId());

    switch (dispatch.target()) {
      case USER -> send(registry.forUser(dispatch.userId()), dispatch.messageJson());
      case USER_PLATFORM ->
          send(
              registry.forUserPlatform(dispatch.userId(), dispatch.platform()),
              dispatch.messageJson());
      case SESSION -> {
        RealtimeConnection connection = registry.bySocketId(dispatch.socketId());
        if (connection != null) {
          send(java.util.List.of(connection), dispatch.messageJson());
        }
      }
      case BROADCAST -> send(registry.all(), dispatch.messageJson());
    }
  }

  public void broadcastLocal(String type, Object payload) {
    send(registry.all(), serialize(type, payload));
  }

  private String serialize(String type, Object payload) {
    try {
      return objectMapper.writeValueAsString(RealtimeEnvelope.of(type, payload));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize realtime event", exception);
    }
  }

  private void send(Collection<RealtimeConnection> connections, String json) {
    if (connections == null || connections.isEmpty()) {
      log.debug("WS_DIAG local_delivery_no_connections bytes={}", json.length());
      return;
    }

    log.debug(
        "WS_DIAG local_delivery_send_attempt connections={} bytes={}",
        connections.size(),
        json.length());
    TextMessage message = new TextMessage(json);
    for (RealtimeConnection connection : connections) {
      try {
        if (connection.session().isOpen()) {
          connection.session().sendMessage(message);
        } else {
          registry.unregister(connection.socketId());
        }
      } catch (IOException | IllegalStateException exception) {
        registry.unregister(connection.socketId());
        log.debug(
            "Realtime send failed socketId={} userId={} reason={}",
            connection.socketId(),
            connection.userId(),
            exception.getClass().getSimpleName());
      }
    }
  }
}
