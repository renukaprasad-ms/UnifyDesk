package com.messaging.managedemail.delivery;

import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.broadcast.entity.BroadcastRun;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedEmailDeliveryGuard {

  private final ManagedEmailRuntimeRepository runtimeRepository;
  private final ManagedEmailRuntimeProperties properties;
  private final ManagedEmailAddressPolicy addressPolicy;
  private final ManagedEmailProviderBudgetService providerBudgetService;

  public Optional<ManagedEmailRuntimeRepository.ManagedSenderRuntime> eligible(
      BroadcastRun run,
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastRecipient recipient) {
    if (!properties.isSendingEnabled() || !properties.reputationPolicyConfigured()) {
      return Optional.empty();
    }

    return runtimeRepository
        .findSenderRuntime(run.getCompanyId(), sender.getId())
        .filter(ManagedEmailRuntimeRepository.ManagedSenderRuntime::isSendable)
        .filter(runtime -> runtime.companyId().equals(run.getCompanyId()))
        .filter(runtime -> runtime.companyId().equals(recipient.getCompanyId()))
        .filter(runtime -> runtime.connectionId().equals(connection.getId()))
        .filter(runtime -> runtime.senderId().equals(run.getSenderId()))
        .filter(runtime -> runtime.senderId().equals(recipient.getSenderId()))
        .filter(
            runtime ->
                addressPolicy.matchesBoundDomain(
                    runtime.senderFromAddress(), runtime.normalizedDomain()))
        .filter(providerBudgetService::hasUsableProviderSnapshot)
        .filter(runtime -> properties.getRegion().equals(runtime.region()));
  }
}
