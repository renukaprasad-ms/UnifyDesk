package com.messaging.managedsms.delivery;

import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.broadcast.entity.BroadcastRun;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeProperties;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
import com.messaging.managedsms.safety.ManagedSmsProviderSafetyService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedSmsDeliveryGuard {

  private final ManagedSmsRuntimeRepository runtimeRepository;
  private final ManagedSmsRuntimeProperties properties;
  private final ManagedSmsProviderSafetyService safetyService;

  public Optional<ManagedSmsRuntimeRepository.DeliveryRuntime> eligible(
      BroadcastRun run,
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastRecipient recipient) {
    if (!properties.providerConfigured()
        || connection.getManagementMode() != BroadcastSenderManagementMode.MESSAGING_MANAGED
        || run.getProviderType() != BroadcastSenderProvider.MSG91_SMS
        || sender.getProvider() != BroadcastSenderProvider.MSG91_SMS
        || connection.getProvider() != BroadcastSenderProvider.MSG91_SMS) {
      return Optional.empty();
    }
    return runtimeRepository
        .findDeliveryRuntime(run.getCompanyId(), run.getId(), sender.getId())
        .filter(ManagedSmsRuntimeRepository.DeliveryRuntime::currentlySendable)
        .filter(runtime -> runtime.companyId().equals(recipient.getCompanyId()))
        .filter(runtime -> runtime.senderId().equals(recipient.getSenderId()))
        .filter(runtime -> runtime.connectionId().equals(connection.getId()))
        .filter(runtime -> safetyService.isAvailable(runtime.providerAccountKey()));
  }
}
