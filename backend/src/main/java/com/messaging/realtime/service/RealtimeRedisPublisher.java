package com.messaging.realtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.realtime.dto.RealtimeDispatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeRedisPublisher {

  private final StringRedisTemplate redisTemplate;
  private final ChannelTopic realtimeChannel;
  private final ObjectMapper objectMapper;

  public void publish(RealtimeDispatch dispatch) {
    try {
      String json = objectMapper.writeValueAsString(dispatch);
      Long subscribers = redisTemplate.convertAndSend(realtimeChannel.getTopic(), json);
      log.debug(
          "WS_DIAG redis_publish target={} userId={} platform={} socketId={} subscribers={} bytes={}",
          dispatch.target(),
          dispatch.userId(),
          dispatch.platform(),
          dispatch.socketId(),
          subscribers,
          json.length());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize realtime dispatch", exception);
    }
  }
}
