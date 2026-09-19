package com.messaging.realtime.service;

import com.messaging.session.entity.SessionPlatform;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

@Component
public class RealtimeConnectionRegistry {

  private final ConcurrentHashMap<String, RealtimeConnection> bySocketId = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, Set<String>> socketIdsByUser = new ConcurrentHashMap<>();

  public void register(Long userId, SessionPlatform platform, WebSocketSession rawSession) {
    WebSocketSession session = new ConcurrentWebSocketSessionDecorator(rawSession, 5_000, 256 * 1024);
    RealtimeConnection connection =
        new RealtimeConnection(rawSession.getId(), userId, platform, session);
    bySocketId.put(rawSession.getId(), connection);
    socketIdsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet())
        .add(rawSession.getId());
  }

  public void unregister(String socketId) {
    RealtimeConnection connection = bySocketId.remove(socketId);
    if (connection == null) {
      return;
    }

    Set<String> ids = socketIdsByUser.get(connection.userId());
    if (ids != null) {
      ids.remove(socketId);
      if (ids.isEmpty()) {
        socketIdsByUser.remove(connection.userId(), ids);
      }
    }
  }

  public Collection<RealtimeConnection> forUser(Long userId) {
    Set<String> ids = socketIdsByUser.get(userId);
    if (ids == null || ids.isEmpty()) {
      return ListSupport.empty();
    }

    Collection<RealtimeConnection> result = new ArrayList<>(ids.size());
    for (String id : ids) {
      RealtimeConnection connection = bySocketId.get(id);
      if (connection != null) {
        result.add(connection);
      }
    }
    return result;
  }

  public Collection<RealtimeConnection> forUserPlatform(Long userId, SessionPlatform platform) {
    return forUser(userId).stream()
        .filter(connection -> connection.platform() == platform)
        .toList();
  }

  public RealtimeConnection bySocketId(String socketId) {
    return bySocketId.get(socketId);
  }

  public Collection<RealtimeConnection> all() {
    return ListSupport.copy(bySocketId.values());
  }

  public record RealtimeConnection(
      String socketId, Long userId, SessionPlatform platform, WebSocketSession session) {}

  private static final class ListSupport {
    private ListSupport() {}

    static <T> Collection<T> empty() {
      return java.util.List.of();
    }

    static <T> Collection<T> copy(Collection<T> values) {
      return java.util.List.copyOf(values);
    }
  }
}
