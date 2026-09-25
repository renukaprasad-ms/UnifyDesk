package com.messaging.broadcast.transport;

import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.common.exception.BadRequestException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class BroadcastEmailTransportRegistry {

  private final Map<BroadcastSenderProvider, BroadcastEmailTransport> transports;

  public BroadcastEmailTransportRegistry(List<BroadcastEmailTransport> transports) {
    EnumMap<BroadcastSenderProvider, BroadcastEmailTransport> mapped =
        new EnumMap<>(BroadcastSenderProvider.class);
    for (BroadcastEmailTransport transport : transports) {
      BroadcastEmailTransport previous = mapped.put(transport.provider(), transport);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate broadcast email transport for " + transport.provider());
      }
    }
    this.transports = Collections.unmodifiableMap(mapped);
  }

  public BroadcastEmailTransport require(BroadcastSenderProvider provider) {
    BroadcastEmailTransport transport = transports.get(provider);
    if (transport == null) {
      throw new BadRequestException(
          "Broadcast delivery is not supported for provider " + provider);
    }
    return transport;
  }
}
