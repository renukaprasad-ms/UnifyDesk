package com.messaging.managedemail.events;

import java.sql.Timestamp;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedEmailDeliveryProjectionService {

  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public void apply(
      ManagedEmailProviderEvent event,
      com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime) {
    Timestamp at = Timestamp.from(event.eventTimestamp());
    jdbcTemplate.update(
        """
        insert into managed_email_recipient_delivery
          (recipient_id, company_id, provider_message_id, last_event_at, updated_at, version)
        values (?, ?, ?, ?, now(), 0)
        on conflict (recipient_id) do update
          set provider_message_id = coalesce(managed_email_recipient_delivery.provider_message_id,
                                             excluded.provider_message_id),
              last_event_at = greatest(managed_email_recipient_delivery.last_event_at,
                                       excluded.last_event_at),
              updated_at = now(),
              version = managed_email_recipient_delivery.version + 1
        """,
        runtime.recipientId(),
        runtime.companyId(),
        event.providerMessageId(),
        at);

    switch (event.type()) {
      case SEND ->
          jdbcTemplate.update(
              """
              update managed_email_recipient_delivery
                 set sent_event_at = coalesce(sent_event_at, ?), updated_at = now()
               where recipient_id = ?
              """,
              at,
              runtime.recipientId());
      case DELIVERY ->
          jdbcTemplate.update(
              """
              update managed_email_recipient_delivery
                 set delivered_at = coalesce(delivered_at, ?), updated_at = now()
               where recipient_id = ?
              """,
              at,
              runtime.recipientId());
      case COMPLAINT ->
          jdbcTemplate.update(
              """
              update managed_email_recipient_delivery
                 set complained_at = coalesce(complained_at, ?), updated_at = now()
               where recipient_id = ?
              """,
              at,
              runtime.recipientId());
      case REJECT, RENDERING_FAILURE ->
          jdbcTemplate.update(
              """
              update managed_email_recipient_delivery
                 set rejected_at = coalesce(rejected_at, ?), updated_at = now()
               where recipient_id = ?
              """,
              at,
              runtime.recipientId());
      case OPEN ->
          jdbcTemplate.update(
              """
              update managed_email_recipient_delivery
                 set first_open_at = coalesce(first_open_at, ?),
                     last_open_at = case
                       when last_open_at is null or last_open_at < ? then ? else last_open_at end,
                     open_count = open_count + 1,
                     updated_at = now()
               where recipient_id = ?
              """,
              at,
              at,
              at,
              runtime.recipientId());
      case CLICK ->
          jdbcTemplate.update(
              """
              update managed_email_recipient_delivery
                 set first_click_at = coalesce(first_click_at, ?),
                     last_click_at = case
                       when last_click_at is null or last_click_at < ? then ? else last_click_at end,
                     click_count = click_count + 1,
                     updated_at = now()
               where recipient_id = ?
              """,
              at,
              at,
              at,
              runtime.recipientId());
      case DELIVERY_DELAY ->
          jdbcTemplate.update(
              """
              update managed_email_recipient_delivery
                 set last_delivery_delay_at = case
                       when last_delivery_delay_at is null or last_delivery_delay_at < ?
                         then ? else last_delivery_delay_at end,
                     updated_at = now()
               where recipient_id = ?
              """,
              at,
              at,
              runtime.recipientId());
      case BOUNCE, SUBSCRIPTION -> {
      }
    }
  }
}
