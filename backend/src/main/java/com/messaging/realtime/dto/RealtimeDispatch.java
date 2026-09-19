package com.messaging.realtime.dto;

import com.messaging.session.entity.SessionPlatform;

public record RealtimeDispatch(
    Target target,
    Long userId,
    SessionPlatform platform,
    String socketId,
    String messageJson) {

  public enum Target {
    USER,
    USER_PLATFORM,
    SESSION,
    BROADCAST
  }
}
