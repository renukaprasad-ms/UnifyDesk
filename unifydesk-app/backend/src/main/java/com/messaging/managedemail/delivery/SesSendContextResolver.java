package com.messaging.managedemail.delivery;

import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.ses.AmazonSesClientManager;
import com.messaging.broadcast.transport.BroadcastTransportException;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SesSendContextResolver {

  private final AmazonSesClientManager customerClientManager;
  private final ManagedSesClientProvider managedClientProvider;
  private final ManagedEmailRuntimeRepository runtimeRepository;
  private final ManagedEmailRuntimeProperties properties;

  public SesSendContext resolve(
      BroadcastSenderConnection connection, BroadcastSender sender) {
    if (connection.getManagementMode() == BroadcastSenderManagementMode.CUSTOMER_MANAGED) {
      return new SesSendContext(
          customerClientManager.client(connection), false, null, null, null);
    }

    ManagedEmailRuntimeRepository.ManagedSenderRuntime runtime =
        runtimeRepository
            .findSenderRuntime(connection.getCompanyId(), sender.getId())
            .orElseThrow(
                () ->
                    BroadcastTransportException.permanent(
                        "MANAGED_EMAIL_RUNTIME_MISMATCH",
                        "UnifyDesk Managed Email sender is not eligible",
                        null));

    if (!runtime.isSendable()
        || !runtime.connectionId().equals(connection.getId())
        || !runtime.companyId().equals(connection.getCompanyId())
        || !runtime.companyId().equals(sender.getCompanyId())
        || properties.getRegion() == null
        || properties.getRegion().isBlank()
        || !properties.getRegion().equals(runtime.region())) {
      throw BroadcastTransportException.permanent(
          "MANAGED_EMAIL_SENDING_SUSPENDED",
          "UnifyDesk Managed Email sending is currently unavailable",
          null);
    }

    return new SesSendContext(
        managedClientProvider.client(runtime.region()),
        true,
        runtime.providerTenantName(),
        runtime.configurationSetName(),
        runtime.domainId());
  }
}
