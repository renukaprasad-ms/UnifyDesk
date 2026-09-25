package com.messaging.channel.dto;

import com.messaging.channel.enums.DisconnectDataPolicy;

public record DisconnectChannelRequest(DisconnectDataPolicy dataPolicy) {

  public DisconnectDataPolicy resolvedDataPolicy() {
    return dataPolicy == null ? DisconnectDataPolicy.KEEP_7_DAYS : dataPolicy;
  }
}
