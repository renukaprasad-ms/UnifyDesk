package com.messaging.broadcast.service;

import com.messaging.broadcast.dto.BroadcastChannelReadinessResponse.Availability;
import com.messaging.broadcast.dto.BroadcastChannelReadinessResponse.Capabilities;
import com.messaging.broadcast.dto.BroadcastChannelReadinessResponse.ChannelReadiness;
import com.messaging.broadcast.dto.BroadcastChannelReadinessResponse.Configuration;
import com.messaging.broadcast.dto.BroadcastChannelReadinessResponse.SenderReadiness;
import com.messaging.broadcast.dto.BroadcastChannelReadinessResponse.SenderState;
import com.messaging.broadcast.dto.BroadcastSenderResponse;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionCapability;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.meta.runtime.MetaWhatsAppControlPlaneProjectionRepository;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.NotFoundException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BroadcastChannelReadinessService {

  private final BroadcastSenderService senderService;
  private final MetaWhatsAppControlPlaneProjectionRepository whatsAppProjectionRepository;
  private final ChannelConnectionRepository channelConnectionRepository;

  public List<ChannelReadiness> readiness(long companyId) {
    List<BroadcastSenderResponse> senders = senderService.list(companyId);
    List<ChannelConnection> broadcastConnections =
        channelConnectionRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
            .filter(
                connection ->
                    connection
                        .getProviderType()
                        .supportsCapability(ChannelConnectionCapability.BROADCAST))
            .toList();
    List<MetaWhatsAppControlPlaneProjectionRepository.Projection> whatsAppProjections =
        whatsAppProjectionRepository.findByCompanyId(companyId);

    Map<ChannelType, List<SenderReadiness>> byChannel = new EnumMap<>(ChannelType.class);
    for (ChannelType channel : ChannelType.values()) {
      byChannel.put(channel, new ArrayList<>());
    }

    for (BroadcastSenderResponse sender : senders) {
      ChannelType channel = sender.provider().channelType();
      SenderState state =
          sender.sendingEnabled() ? SenderState.READY : SenderState.ACTION_REQUIRED;
      byChannel
          .get(channel)
          .add(
              new SenderReadiness(
                  sender.id(),
                  sender.displayName() == null || sender.displayName().isBlank()
                      ? sender.fromAddress()
                      : sender.displayName(),
                  sender.fromAddress(),
                  state,
                  state == SenderState.READY ? null : nextAction(channel),
                  capabilities(sender)));
    }

    List<ChannelReadiness> result = new ArrayList<>();
    for (ChannelType channel : ChannelType.values()) {
      List<SenderReadiness> channelSenders = List.copyOf(byChannel.get(channel));
      boolean anyReady =
          channelSenders.stream().anyMatch(sender -> sender.state() == SenderState.READY);
      List<ChannelConnection> channelConnections =
          broadcastConnections.stream()
              .filter(connection -> connection.getChannelType() == channel)
              .toList();

      Configuration configuration;
      if (anyReady) {
        boolean degradedWhatsApp =
            channel == ChannelType.WHATSAPP
                && whatsAppProjections.stream()
                    .anyMatch(
                        projection ->
                            "DEGRADED".equals(projection.productState())
                                && projection.usableForSend());
        configuration = degradedWhatsApp ? Configuration.DEGRADED : Configuration.READY;
      } else if (channelConnections.isEmpty()) {
        configuration = Configuration.NOT_CONFIGURED;
      } else if (channelConnections.stream()
          .allMatch(connection -> connection.getStatus() == ChannelConnectionStatus.DISCONNECTED)) {
        configuration = Configuration.DISCONNECTED;
      } else {
        configuration = Configuration.ACTION_REQUIRED;
      }

      result.add(
          new ChannelReadiness(
              channel,
              Availability.AVAILABLE,
              configuration,
              channelSenders,
              anyReady ? null : nextAction(channel)));
    }
    return List.copyOf(result);
  }

  public SenderReadiness sender(long companyId, long senderId) {
    return readiness(companyId).stream()
        .flatMap(channel -> channel.senders().stream())
        .filter(sender -> Long.toString(senderId).equals(sender.senderId()))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Broadcast sender not found"));
  }

  private Capabilities capabilities(BroadcastSenderResponse sender) {
    if (sender.provider() == BroadcastSenderProvider.META_WHATSAPP) {
      return new Capabilities(true, true, true, true, true, true);
    }
    boolean managedDeliveryEvents =
        sender.managementMode() == BroadcastSenderManagementMode.MESSAGING_MANAGED
            && (sender.provider() == BroadcastSenderProvider.AMAZON_SES
                || sender.provider() == BroadcastSenderProvider.MSG91_SMS);
    return new Capabilities(true, managedDeliveryEvents, false, false, false, false);
  }

  private String nextAction(ChannelType channel) {
    return switch (channel) {
      case EMAIL -> "Configure or reconnect an Email Broadcast sender";
      case SMS -> "Configure or repair an SMS sender";
      case WHATSAPP -> "Connect or repair a Meta WhatsApp phone";
    };
  }
}
