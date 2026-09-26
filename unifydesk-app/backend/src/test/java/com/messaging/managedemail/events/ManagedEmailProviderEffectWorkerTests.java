package com.messaging.managedemail.events;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.managedemail.events.ManagedEmailProviderEffectRepository.ClaimedProviderEffect;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sesv2.model.SuppressionListReason;

class ManagedEmailProviderEffectWorkerTests {

  @Test
  void transientFailureMovesTaskToRetry() {
    Fixture fixture = fixture();
    ClaimedProviderEffect effect = effect(1);
    when(fixture.repository.claimNext(any(), any())).thenReturn(Optional.of(effect));
    doThrow(new RuntimeException("temporary"))
        .when(fixture.suppression)
        .signal(fixture.runtime, SuppressionListReason.BOUNCE);

    fixture.worker.runOnce();

    verify(fixture.repository)
        .retry(eq(90L), eq(1), any(Instant.class), eq("RuntimeException"));
    verify(fixture.repository, never()).failed(anyLong(), anyInt(), any(), anyString());
  }

  @Test
  void permanentFailureIsOperatorVisible() {
    Fixture fixture = fixture();
    ClaimedProviderEffect effect = effect(1);
    when(fixture.repository.claimNext(any(), any())).thenReturn(Optional.of(effect));
    doThrow(new IllegalArgumentException("invalid provider effect"))
        .when(fixture.suppression)
        .signal(fixture.runtime, SuppressionListReason.BOUNCE);

    fixture.worker.runOnce();

    verify(fixture.repository)
        .failed(eq(90L), eq(1), any(Instant.class), eq("IllegalArgumentException"));
  }

  @Test
  void exhaustedTransientFailureBecomesFailed() {
    Fixture fixture = fixture();
    fixture.properties.setProviderEffectMaxAttempts(2);
    ClaimedProviderEffect effect = effect(2);
    when(fixture.repository.claimNext(any(), any())).thenReturn(Optional.of(effect));
    doThrow(new RuntimeException("temporary"))
        .when(fixture.suppression)
        .signal(fixture.runtime, SuppressionListReason.BOUNCE);

    fixture.worker.runOnce();

    verify(fixture.repository)
        .failed(eq(90L), eq(2), any(Instant.class), eq("RuntimeException"));
  }

  @Test
  void successMarksExactClaimSucceeded() {
    Fixture fixture = fixture();
    ClaimedProviderEffect effect = effect(3);
    when(fixture.repository.claimNext(any(), any())).thenReturn(Optional.of(effect));

    fixture.worker.runOnce();

    verify(fixture.suppression).signal(fixture.runtime, SuppressionListReason.BOUNCE);
    verify(fixture.repository).succeeded(eq(90L), eq(3), any(Instant.class));
  }

  private Fixture fixture() {
    ManagedEmailProviderEffectRepository repository =
        mock(ManagedEmailProviderEffectRepository.class);
    ManagedEmailRuntimeRepository runtimeRepository =
        mock(ManagedEmailRuntimeRepository.class);
    ManagedEmailProviderSuppressionService suppression =
        mock(ManagedEmailProviderSuppressionService.class);
    ManagedEmailRuntimeProperties properties = new ManagedEmailRuntimeProperties();
    properties.setProviderEffectMaxAttempts(8);
    properties.setProviderEffectRetryInitialSeconds(1);
    properties.setProviderEffectRetryMaxSeconds(60);
    properties.setProviderEffectStaleAfterSeconds(10);
    ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime = runtime();
    when(runtimeRepository.findRecipientRuntime(70L)).thenReturn(Optional.of(runtime));
    ManagedEmailProviderEffectWorker worker =
        new ManagedEmailProviderEffectWorker(
            repository, runtimeRepository, suppression, properties);
    return new Fixture(worker, repository, suppression, properties, runtime);
  }

  private ClaimedProviderEffect effect(int attempt) {
    return new ClaimedProviderEffect(
        90L, 80L, 10L, 50L, 70L, SuppressionListReason.BOUNCE, attempt);
  }

  private ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime() {
    return new ManagedEmailRuntimeRepository.ManagedRecipientRuntime(
        70L, 10L, 60L, 20L, 30L, 50L, 40L, "ACCEPTED_BY_PROVIDER",
        "recipient@example.test", "provider-1", "corr-70", "offers@example.test",
        "ACTIVE", "ACTIVE", "MESSAGING_MANAGED", true, true, "ACTIVE", "ENABLED",
        "HEALTHY", "ESTABLISHED", "ACTIVE", "example.test", "us-east-1",
        "tenant-10", "cfg-10", 20.0, 10000.0, 100.0, Instant.now());
  }

  private record Fixture(
      ManagedEmailProviderEffectWorker worker,
      ManagedEmailProviderEffectRepository repository,
      ManagedEmailProviderSuppressionService suppression,
      ManagedEmailRuntimeProperties properties,
      ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime) {}
}
