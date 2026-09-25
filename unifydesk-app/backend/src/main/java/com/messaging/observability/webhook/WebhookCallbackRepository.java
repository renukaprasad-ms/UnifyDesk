package com.messaging.observability.webhook;

import com.messaging.channel.enums.ProviderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookCallbackRepository extends JpaRepository<WebhookCallback, Long> {

  @Query(
      """
      select callback
      from WebhookCallback callback
      where (:provider is null or callback.providerType = :provider)
        and (:status is null or callback.status = :status)
        and (
          :search = ''
          or lower(coalesce(callback.eventId, '')) like lower(concat('%', :search, '%'))
          or lower(callback.callbackType) like lower(concat('%', :search, '%'))
          or lower(callback.endpoint) like lower(concat('%', :search, '%'))
          or lower(coalesce(callback.errorMessage, '')) like lower(concat('%', :search, '%'))
        )
      """)
  Page<WebhookCallback> search(
      @Param("provider") ProviderType provider,
      @Param("status") WebhookCallbackStatus status,
      @Param("search") String search,
      Pageable pageable);
}
