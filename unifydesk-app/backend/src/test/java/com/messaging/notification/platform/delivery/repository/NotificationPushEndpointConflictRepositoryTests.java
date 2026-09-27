package com.messaging.notification.platform.delivery.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationPushEndpointConflictRepositoryTests {

  @Test
  void endpointInsertUsesNonAbortingPostgresConflictPath() {
    String sql =
        NotificationDeliveryRuntimeRepository.PUSH_SUBSCRIPTION_INSERT_SQL
            .replaceAll("\\s+", " ")
            .trim()
            .toLowerCase();

    assertThat(sql)
        .contains("on conflict (endpoint_hash) do nothing")
        .contains("returning *");
  }
}
