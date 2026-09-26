package com.messaging.managedemail.events;

import com.messaging.managedemail.delivery.ManagedSesClientProvider;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sesv2.model.PutSuppressedDestinationRequest;
import software.amazon.awssdk.services.sesv2.model.SuppressionListReason;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagedEmailProviderSuppressionService {

  private final ManagedSesClientProvider clientProvider;

  public void signal(
      ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime,
      SuppressionListReason reason) {
    try {
      clientProvider
          .client(runtime.region())
          .putSuppressedDestination(
              PutSuppressedDestinationRequest.builder()
                  .emailAddress(runtime.normalizedDestination())
                  .reason(reason)
                  .tenantName(runtime.providerTenantName())
                  .build());
    } catch (RuntimeException exception) {
      log.warn(
          "managed_email_provider_suppression_retry_required companyId={} tenantId={} recipientId={} reason={}",
          runtime.companyId(),
          runtime.tenantId(),
          runtime.recipientId(),
          reason);
      throw exception;
    }
  }
}
