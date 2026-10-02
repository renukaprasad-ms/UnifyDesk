package com.messaging.broadcast.dto;

import com.messaging.channel.enums.ChannelType;
import java.util.List;

public final class BroadcastChannelReadinessResponse {

  private BroadcastChannelReadinessResponse() {}

  public enum Availability {
    AVAILABLE,
    UNAVAILABLE
  }

  public enum Configuration {
    NOT_CONFIGURED,
    ACTION_REQUIRED,
    READY,
    DEGRADED,
    DISCONNECTED
  }

  public enum SenderState {
    READY,
    ACTION_REQUIRED,
    BLOCKED,
    DISCONNECTED,
    TEMPORARILY_UNAVAILABLE
  }

  public record Capabilities(
      boolean providerAcceptance,
      boolean deliveryEvents,
      boolean readEvents,
      boolean inboundReplies,
      boolean templateRequired,
      boolean freeFormSessionSupported) {}

  public record SenderReadiness(
      String senderId,
      String label,
      String addressOrNumber,
      SenderState state,
      String nextAction,
      Capabilities capabilities) {}

  public record ChannelReadiness(
      ChannelType channel,
      Availability availability,
      Configuration configuration,
      List<SenderReadiness> senders,
      String nextAction) {}
}
