package com.messaging.realtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.realtime.dto.RealtimeDispatch;
import com.messaging.realtime.dto.RealtimeEnvelope;
import com.messaging.session.entity.SessionPlatform;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RealtimeService {

  private final CompanyMemberRepository companyMemberRepository;
  private final RealtimeRedisPublisher redisPublisher;
  private final ObjectMapper objectMapper;

  public void sendToAllUserSessions(Long userId, String type, Object payload) {
    publish(
        new RealtimeDispatch(
            RealtimeDispatch.Target.USER, userId, null, null, serialize(type, payload)));
  }

  public void sendToUserId(Long userId, String type, Object payload) {
    sendToAllUserSessions(userId, type, payload);
  }

  public void sendToUserPlatform(
      Long userId, SessionPlatform platform, String type, Object payload) {
    publish(
        new RealtimeDispatch(
            RealtimeDispatch.Target.USER_PLATFORM,
            userId,
            platform,
            null,
            serialize(type, payload)));
  }

  public void sendToSession(String socketId, String type, Object payload) {
    publish(
        new RealtimeDispatch(
            RealtimeDispatch.Target.SESSION, null, null, socketId, serialize(type, payload)));
  }

  public void sendToCompany(Long companyId, String type, Object payload) {
    companyMemberRepository.findByCompanyIdAndStatus(companyId, CompanyMemberStatus.ACTIVE).stream()
        .map(member -> member.getUser().getId())
        .distinct()
        .forEach(userId -> sendToAllUserSessions(userId, type, payload));
  }

  public void broadcast(String type, Object payload) {
    publish(
        new RealtimeDispatch(
            RealtimeDispatch.Target.BROADCAST, null, null, null, serialize(type, payload)));
  }

  private void publish(RealtimeDispatch dispatch) {
    redisPublisher.publish(dispatch);
  }

  private String serialize(String type, Object payload) {
    try {
      return objectMapper.writeValueAsString(RealtimeEnvelope.of(type, payload));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize realtime event", exception);
    }
  }
}
