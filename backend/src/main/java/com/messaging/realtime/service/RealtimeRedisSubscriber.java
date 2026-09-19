package com.messaging.realtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.realtime.dto.RealtimeDispatch;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeRedisSubscriber implements MessageListener {

  private final ObjectMapper objectMapper;
  private final RealtimeLocalDeliveryService localDeliveryService;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      RealtimeDispatch dispatch =
          objectMapper.readValue(
              new String(message.getBody(), StandardCharsets.UTF_8), RealtimeDispatch.class);
      localDeliveryService.deliver(dispatch);
    } catch (Exception exception) {
      log.warn("Ignoring invalid realtime Redis message reason={}", exception.getClass().getSimpleName());
    }
  }
}
