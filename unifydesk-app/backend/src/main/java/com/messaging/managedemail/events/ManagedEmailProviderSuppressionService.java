package com.messaging.managedemail.events;

import com.messaging.managedemail.delivery.ManagedSesClientProvider;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.GetSuppressedDestinationRequest;
import software.amazon.awssdk.services.sesv2.model.NotFoundException;
import software.amazon.awssdk.services.sesv2.model.PutSuppressedDestinationRequest;
import software.amazon.awssdk.services.sesv2.model.SuppressionListReason;

@Service
@RequiredArgsConstructor
public class ManagedEmailProviderSuppressionService {

  private final ManagedSesClientProvider clientProvider;

  public void signal(
      ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime,
      SuppressionListReason reason) {
    SesV2Client client = clientProvider.client(runtime.region());
    if (alreadySuppressed(client, runtime, reason)) {
      return;
    }
    client.putSuppressedDestination(
        PutSuppressedDestinationRequest.builder()
            .emailAddress(runtime.normalizedDestination())
            .reason(reason)
            .tenantName(runtime.providerTenantName())
            .build());
  }

  private boolean alreadySuppressed(
      SesV2Client client,
      ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime,
      SuppressionListReason reason) {
    try {
      var response =
          client.getSuppressedDestination(
              GetSuppressedDestinationRequest.builder()
                  .emailAddress(runtime.normalizedDestination())
                  .tenantName(runtime.providerTenantName())
                  .build());
      return response.suppressedDestination() != null
          && reason.toString().equals(response.suppressedDestination().reasonAsString())
          && runtime.providerTenantName().equals(response.suppressedDestination().tenantName());
    } catch (NotFoundException exception) {
      return false;
    }
  }
}
