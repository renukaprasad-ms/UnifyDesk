package com.messaging.realtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.realtime.dto.RealtimeEnvelope;
import com.messaging.realtime.service.RealtimeConnectionRegistry.RealtimeConnection;
import com.messaging.session.entity.SessionPlatform;
import java.io.IOException;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeService {

  private final RealtimeConnectionRegistry registry;
  private final CompanyMemberRepository companyMemberRepository;
  private final ObjectMapper objectMapper;

  public void sendToAllUserSessions(Long userId, String type, Object payload) {
    send(registry.forUser(userId), type, payload);
  }

  public void sendToUserId(Long userId, String type, Object payload) {
    sendToAllUserSessions(userId, type, payload);
  }

  public void sendToUserPlatform(
      Long userId, SessionPlatform platform, String type, Object payload) {
    send(registry.forUserPlatform(userId, platform), type, payload);
  }

  public void sendToSession(String socketId, String type, Object payload) {
    RealtimeConnection connection = registry.bySocketId(socketId);
    if (connection != null) {
      send(java.util.List.of(connection), type, payload);
    }
  }

  public void sendToCompany(Long companyId, String type, Object payload) {
    companyMemberRepository.findByCompanyIdAndStatus(companyId, CompanyMemberStatus.ACTIVE).stream()
        .map(member -> member.getUser().getId())
        .distinct()
        .forEach(userId -> sendToAllUserSessions(userId, type, payload));
  }

  public void broadcast(String type, Object payload) {
    send(registry.all(), type, payload);
  }

  private void send(Collection<RealtimeConnection> connections, String type, Object payload) {
    if (connections.isEmpty()) {
      return;
    }

    String json;
    try {
      json = objectMapper.writeValueAsString(RealtimeEnvelope.of(type, payload));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize realtime event", exception);
    }

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
