package com.messaging.managedemail.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.messaging.managedemail.delivery.ManagedSesClientProvider;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

class ManagedEmailProviderSuppressionServiceTests {

  @Test
  void retryAfterUnknownCompletionReconcilesExistingTenantSuppression() {
    ManagedSesClientProvider provider = mock(ManagedSesClientProvider.class);
    SesV2Client client = mock(SesV2Client.class);
    when(provider.client("us-east-1")).thenReturn(client);
    when(client.getSuppressedDestination(any(GetSuppressedDestinationRequest.class)))
        .thenThrow(NotFoundException.builder().message("missing").build())
        .thenReturn(
            GetSuppressedDestinationResponse.builder()
                .suppressedDestination(
                    SuppressedDestination.builder()
                        .emailAddress("recipient@example.test")
                        .reason(SuppressionListReason.BOUNCE)
                        .tenantName("tenant-10")
                        .lastUpdateTime(Instant.now())
                        .build())
                .build());

    ManagedEmailProviderSuppressionService service =
        new ManagedEmailProviderSuppressionService(provider);
    ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime = runtime();

    service.signal(runtime, SuppressionListReason.BOUNCE);
    service.signal(runtime, SuppressionListReason.BOUNCE);

    verify(client, times(2)).getSuppressedDestination(any(GetSuppressedDestinationRequest.class));
    verify(client, times(1)).putSuppressedDestination(any(PutSuppressedDestinationRequest.class));
  }

  private ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime() {
    return new ManagedEmailRuntimeRepository.ManagedRecipientRuntime(
        70L, 10L, 60L, 20L, 30L, 50L, 40L, "ACCEPTED_BY_PROVIDER",
        "recipient@example.test", "provider-1", "corr-70", "offers@example.test",
        "ACTIVE", "ACTIVE", "MESSAGING_MANAGED", true, true, "ACTIVE", "ENABLED",
        "HEALTHY", "ESTABLISHED", "ACTIVE", "example.test", "us-east-1",
        "tenant-10", "cfg-10", 20.0, 10000.0, 100.0, Instant.now());
  }
}
