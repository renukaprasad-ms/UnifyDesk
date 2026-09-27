package com.messaging.notification.platform.core.state;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class NotificationUnreadCounterServiceTests {

  @Test
  void readUnreadTransitionsUseAtomicNonNegativeCounterMutation() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    NotificationUnreadCounterService service = new NotificationUnreadCounterService(jdbc);

    service.increment(7L, 22L);
    service.decrement(7L, 22L);

    verify(jdbc, times(2))
        .update(contains("greatest(notification_unread_counters.unread_count + ?,0)"), any(Object[].class));
  }
}
