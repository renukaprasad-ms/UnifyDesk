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
      String body = new String(message.getBody(), StandardCharsets.UTF_8);
      RealtimeDispatch dispatch = objectMapper.readValue(body, RealtimeDispatch.class);
      log.debug(
          "WS_DIAG redis_receive target={} userId={} platform={} socketId={} bytes={}",
          dispatch.target(),
          dispatch.userId(),
          dispatch.platform(),
          dispatch.socketId(),
          body.length());
      localDeliveryService.deliver(dispatch);
    } catch (Exception exception) {
      log.warn(
          "Ignoring invalid realtime Redis message reason={}",
          exception.getClass().getSimpleName());
    }
  }
}
