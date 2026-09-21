package com.messaging.broadcast.transport;

import com.messaging.channel.enums.ProviderType;
import com.messaging.common.exception.BadRequestException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class BroadcastEmailTransportRegistry {

  private final Map<ProviderType, BroadcastEmailTransport> transports;

  public BroadcastEmailTransportRegistry(List<BroadcastEmailTransport> transports) {
    EnumMap<ProviderType, BroadcastEmailTransport> mapped = new EnumMap<>(ProviderType.class);
    for (BroadcastEmailTransport transport : transports) {
      BroadcastEmailTransport previous = mapped.put(transport.providerType(), transport);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate broadcast email transport for " + transport.providerType());
      }
    }
    this.transports = Collections.unmodifiableMap(mapped);
  }

  public BroadcastEmailTransport require(ProviderType providerType) {
    BroadcastEmailTransport transport = transports.get(providerType);
    if (transport == null) {
      throw new BadRequestException(
          "Broadcast email delivery is not supported for provider " + providerType);
    }
    return transport;
  }
}
