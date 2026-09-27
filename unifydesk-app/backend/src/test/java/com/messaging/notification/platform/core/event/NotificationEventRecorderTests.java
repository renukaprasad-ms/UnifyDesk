package com.messaging.notification.platform.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.notification.platform.core.registry.NotificationTypeRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class NotificationEventRecorderTests {

  @Test
  void duplicateCompanyCreatedEventReturnsExistingLogicalEvent() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
    when(jdbc.queryForObject(
            org.mockito.ArgumentMatchers.contains("where event_key=?"),
            org.mockito.ArgumentMatchers.eq(Long.class),
            org.mockito.ArgumentMatchers.eq("company.created:99")))
        .thenReturn(123L);
    NotificationEventRecorder recorder =
        new NotificationEventRecorder(jdbc, new ObjectMapper(), new NotificationTypeRegistry());

    long firstLogicalId = recorder.recordCompanyCreated(99L, 7L, "Acme");

    assertThat(firstLogicalId).isEqualTo(123L);
  }

  @Test
  void companyCreatedPersistsCreatorAsDirectRecipientNotBusinessAddress() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    NotificationEventRecorder recorder =
        new NotificationEventRecorder(jdbc, new ObjectMapper(), new NotificationTypeRegistry());

    recorder.recordCompanyCreated(99L, 7L, "Acme");

    ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(anyString(), values.capture());
    Object[] args = values.getValue();
    assertThat(args).contains("company.created:99", "company.created", "USER", 7L, "COMPANY", "99");
    assertThat(args).doesNotContain("business@example.com");
  }
}
