package com.messaging.realtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.realtime.dto.RealtimeDispatch;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RealtimeRedisPublisher {

  private final StringRedisTemplate redisTemplate;
  private final ChannelTopic realtimeChannel;
  private final ObjectMapper objectMapper;

  public void publish(RealtimeDispatch dispatch) {
    try {
      redisTemplate.convertAndSend(
          realtimeChannel.getTopic(), objectMapper.writeValueAsString(dispatch));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize realtime dispatch", exception);
    }
  }
}
