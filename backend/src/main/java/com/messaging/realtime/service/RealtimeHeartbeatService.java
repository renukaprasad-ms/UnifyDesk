package com.messaging.realtime.service;

import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RealtimeHeartbeatService {

  private final RealtimeService realtimeService;

  @Scheduled(fixedDelayString = "${app.realtime.heartbeat-interval-ms:30000}")
  public void heartbeat() {
    realtimeService.broadcast("realtime.heartbeat", Map.of("at", Instant.now().toString()));
  }
}
