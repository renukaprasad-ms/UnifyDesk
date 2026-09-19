package com.messaging.realtime.config;

import com.messaging.realtime.websocket.RealtimeHandshakeInterceptor;
import com.messaging.realtime.websocket.RealtimeWebSocketHandler;
import java.util.Arrays;
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
  private final RealtimeHandshakeInterceptor handshakeInterceptor;

  @Value("${app.realtime.allowed-origins:https://messaging.renukaprasad.in}")
  private String allowedOrigins;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(handler, "/api/v1/realtime/ws")
        .addInterceptors(handshakeInterceptor)
        .setAllowedOrigins(
            Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new));
  }
}
