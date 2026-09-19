package com.messaging.realtime.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.realtime.dto.RealtimeEnvelope;
import com.messaging.realtime.service.RealtimeConnectionRegistry;
import com.messaging.session.entity.SessionPlatform;
import java.net.URI;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

  private final RealtimeConnectionRegistry registry;
  private final ObjectMapper objectMapper;

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    log.info(
        "WS_DIAG handler_established_enter socketId={} principal={} uri={} remote={}",
        session.getId(),
        session.getPrincipal() == null ? "none" : session.getPrincipal().getName(),
        session.getUri(),
        session.getRemoteAddress());

    if (session.getPrincipal() == null) {
      log.warn("WS_DIAG handler_reject socketId={} reason=no_principal", session.getId());
      session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Authentication required"));
      return;
    }

    Long userId;
    try {
      userId = Long.parseLong(session.getPrincipal().getName());
    } catch (NumberFormatException exception) {
      log.warn(
          "WS_DIAG handler_reject socketId={} reason=invalid_principal principal={}",
          session.getId(),
          session.getPrincipal().getName());
      session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid principal"));
      return;
    }

    SessionPlatform platform = platform(session.getUri());
    registry.register(userId, platform, session);
    log.info(
        "WS_DIAG handler_registered socketId={} userId={} platform={}",
        session.getId(),
        userId,
        platform);
    session.sendMessage(
        new TextMessage(
            objectMapper.writeValueAsString(
                RealtimeEnvelope.of(
                    "realtime.connected",
                    new ConnectedPayload(session.getId(), userId.toString(), platform.name())))));
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    if ("ping".equalsIgnoreCase(message.getPayload().trim())) {
      log.debug("WS_DIAG heartbeat_ping socketId={}", session.getId());
      session.sendMessage(
          new TextMessage(
              objectMapper.writeValueAsString(RealtimeEnvelope.of("realtime.pong", null))));
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    log.info(
        "WS_DIAG handler_closed socketId={} code={} reason={}",
        session.getId(),
        status.getCode(),
        status.getReason());
    registry.unregister(session.getId());
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    log.warn(
        "WS_DIAG transport_error socketId={} open={} reason={}",
        session.getId(),
        session.isOpen(),
        exception == null ? "unknown" : exception.getClass().getSimpleName());
    registry.unregister(session.getId());
  }

  private SessionPlatform platform(URI uri) {
    if (uri == null) {
      return SessionPlatform.WEB;
    }
    String value = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("platform");
    if (value == null || value.isBlank()) {
      return SessionPlatform.WEB;
    }
    try {
      return SessionPlatform.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      return SessionPlatform.WEB;
    }
  }

  private record ConnectedPayload(String socketId, String userId, String platform) {}
}
