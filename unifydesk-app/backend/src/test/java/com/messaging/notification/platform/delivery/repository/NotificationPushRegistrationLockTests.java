package com.messaging.notification.platform.delivery.repository;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class NotificationPushRegistrationLockTests {

  @Test
  void registrationLockUsesStableUserRowForUpdate() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    NotificationDeliveryRuntimeRepository repository =
        new NotificationDeliveryRuntimeRepository(jdbcTemplate);

    repository.lockUserForPushRegistration(7L);

    verify(jdbcTemplate)
        .queryForObject(
            eq("select id from users where id=? for update"),
            eq(Long.class),
            eq(7L));
  }
}
