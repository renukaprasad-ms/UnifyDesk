package com.messaging.notification.platform.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.notification.platform.core.entity.NotificationEvent;
import com.messaging.notification.platform.core.registry.NotificationTypeRegistry;
import com.messaging.notification.platform.core.repository.NotificationEventJpaRepository;
import com.messaging.notification.platform.core.repository.NotificationEventRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationEventServiceTests {
  @Test
  void duplicateCompanyCreatedEventReturnsExistingLogicalEvent() {
    NotificationEventJpaRepository repository = mock(NotificationEventJpaRepository.class);
    NotificationEvent existing = new NotificationEvent();
    existing.setId(123L);
    when(repository.insertIfAbsent(any())).thenReturn(0);
    when(repository.findByEventKey("company.created:99")).thenReturn(Optional.of(existing));
    NotificationEventService service =
        new NotificationEventService(
            new NotificationEventRepository(repository),
            new ObjectMapper(),
            new NotificationTypeRegistry());

    assertThat(service.recordCompanyCreated(99L, 7L, "Acme")).isEqualTo(123L);
  }

  @Test
  void companyCreatedPersistsCreatorAsDirectRecipientNotBusinessAddress() {
    NotificationEventJpaRepository repository = mock(NotificationEventJpaRepository.class);
    when(repository.insertIfAbsent(any())).thenReturn(1);
    NotificationEventService service =
        new NotificationEventService(
            new NotificationEventRepository(repository),
            new ObjectMapper(),
            new NotificationTypeRegistry());

    long id = service.recordCompanyCreated(99L, 7L, "Acme");

    ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
    verify(repository).insertIfAbsent(captor.capture());
    NotificationEvent event = captor.getValue();
    assertThat(event.getId()).isEqualTo(id);
    assertThat(event.getEventKey()).isEqualTo("company.created:99");
    assertThat(event.getCompanyId()).isEqualTo(99L);
    assertThat(event.getRecipientMode()).isEqualTo("USER");
    assertThat(event.getRecipientUserId()).isEqualTo(7L);
    assertThat(event.getPublishState()).isEqualTo("PENDING");
    assertThat(event.getPublishAttempts()).isZero();
    assertThat(event.getVariablesJson()).contains("Acme");
    assertThat(event.getNextPublishAt()).isNotNull();
  }
}
