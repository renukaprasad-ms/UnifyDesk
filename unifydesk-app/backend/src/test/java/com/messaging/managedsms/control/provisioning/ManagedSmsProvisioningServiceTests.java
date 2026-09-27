package com.messaging.managedsms.control.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.messaging.managedsms.control.entity.ManagedSmsProvisioningOperation;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import com.messaging.managedsms.control.repository.ManagedSmsProvisioningOperationRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManagedSmsProvisioningServiceTests {

  @Test
  void enqueueIsIdempotentForSameOperationKey() {
    ManagedSmsProvisioningOperationRepository repository =
        mock(ManagedSmsProvisioningOperationRepository.class);
    ManagedSmsProvisioningOperation existing = new ManagedSmsProvisioningOperation();
    existing.setId(10L);
    existing.setStatus(ProvisioningOperationStatus.ACTION_REQUIRED);
    when(repository.findByIdempotencyKey("same-key")).thenReturn(Optional.of(existing));

    ManagedSmsProvisioningService service = new ManagedSmsProvisioningService(repository);
    var result =
        service.enqueue(
            1L,
            2L,
            ProvisioningResourceType.HEADER,
            3L,
            ProvisioningOperationType.ENSURE_HEADER_MAPPING,
            "same-key");

    assertThat(result).isSameAs(existing);
    verify(repository, never()).save(any());
  }

  @Test
  void explicitRefreshResetsActionRequiredOperationForRetry() {
    ManagedSmsProvisioningOperationRepository repository =
        mock(ManagedSmsProvisioningOperationRepository.class);
    ManagedSmsProvisioningOperation existing = new ManagedSmsProvisioningOperation();
    existing.setId(10L);
    existing.setStatus(ProvisioningOperationStatus.ACTION_REQUIRED);
    existing.setLastErrorCode("MANUAL");
    existing.setLastErrorMessage("review");
    when(repository.findByIdempotencyKey("same-key")).thenReturn(Optional.of(existing));
    when(repository.save(any(ManagedSmsProvisioningOperation.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ManagedSmsProvisioningService service = new ManagedSmsProvisioningService(repository);
    var result =
        service.enqueueOrReset(
            1L,
            2L,
            ProvisioningResourceType.HEADER,
            3L,
            ProvisioningOperationType.REFRESH_HEADER_MAPPING,
            "same-key");

    assertThat(result.getStatus()).isEqualTo(ProvisioningOperationStatus.PENDING);
    assertThat(result.getNextAttemptAt()).isNotNull();
    assertThat(result.getLastErrorCode()).isNull();
    assertThat(result.getLastErrorMessage()).isNull();
  }
}
