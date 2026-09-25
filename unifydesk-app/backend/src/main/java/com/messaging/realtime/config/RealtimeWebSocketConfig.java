package com.messaging.realtime.config;

import com.messaging.realtime.websocket.RealtimeWebSocketHandler;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class RealtimeWebSocketConfig implements WebSocketConfigurer {

  private final RealtimeWebSocketHandler handler;

  @Value("${app.realtime.allowed-origins:https://app.unifydesk.in}")
  private String allowedOrigins;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    Set<String> origins = new LinkedHashSet<>();
    Arrays.stream(allowedOrigins.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .forEach(origins::add);

    // Keep the canonical production app origin valid even when an older
    // REALTIME_ALLOWED_ORIGINS value is still present in deployment secrets.
    origins.add("https://app.unifydesk.in");

    registry
        .addHandler(handler, "/api/v1/realtime/ws")
        .setAllowedOrigins(origins.toArray(String[]::new));
  }
}
