package com.messaging.realtime.websocket;

import java.security.Principal;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Slf4j
@Component
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    Principal principal = request.getPrincipal();
    log.info(
        "WS_DIAG handshake_enter uri={} principal={} origin={} upgrade={} connection={} forwardedProto={} forwardedHost={} remote={}",
        request.getURI(),
        principal == null ? "none" : principal.getName(),
        header(request, "Origin"),
        header(request, "Upgrade"),
        header(request, "Connection"),
        header(request, "X-Forwarded-Proto"),
        header(request, "X-Forwarded-Host"),
        request.getRemoteAddress());
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {
    log.info(
        "WS_DIAG handshake_exit uri={} exception={}",
        request.getURI(),
        exception == null ? "none" : exception.getClass().getSimpleName());
  }

  private String header(ServerHttpRequest request, String name) {
    String value = request.getHeaders().getFirst(name);
    return value == null || value.isBlank() ? "none" : value;
  }
}
