package com.messaging.channel.meta.runtime;

import com.messaging.common.id.IdGenerator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class MetaWhatsAppProviderMediaReservationStore {

  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public Reservation reserveOrGet(
      long connectionId, long inboxMessageId, String providerMediaId) {
    long reservationId = IdGenerator.nextId();
    long reservedMediaId = IdGenerator.nextId();
    jdbcTemplate.update(
        """
        insert into meta_whatsapp_provider_media_reservations
          (id,connection_id,inbox_message_id,provider_media_id,reserved_media_id,
           state,created_at,updated_at)
        values (?,?,?,?,?,'RESERVED',now(),now())
        on conflict (connection_id,provider_media_id) do nothing
        """,
        reservationId,
        connectionId,
        inboxMessageId,
        providerMediaId,
        reservedMediaId);

    List<Reservation> rows =
        jdbcTemplate.query(
            """
            select id,connection_id,inbox_message_id,provider_media_id,reserved_media_id,state
            from meta_whatsapp_provider_media_reservations
            where connection_id=? and provider_media_id=?
            for update
            """,
            (rs, rowNum) ->
                new Reservation(
                    rs.getLong("id"),
                    rs.getLong("connection_id"),
                    rs.getLong("inbox_message_id"),
                    rs.getString("provider_media_id"),
                    rs.getLong("reserved_media_id"),
                    rs.getString("state")),
            connectionId,
            providerMediaId);
    if (rows.isEmpty()) {
      throw new MetaWhatsAppRuntimeException(
          "WHATSAPP_MEDIA_RESERVATION_FAILED", true, "Provider media reservation was not visible");
    }
    Reservation reservation = rows.getFirst();
    if (reservation.inboxMessageId() != inboxMessageId) {
      throw new MetaWhatsAppRuntimeException(
          "WHATSAPP_MEDIA_RESERVATION_CONFLICT",
          false,
          "Provider media is already reserved for another message");
    }
    return reservation;
  }

  public void markIngested(long reservationId) {
    jdbcTemplate.update(
        """
        update meta_whatsapp_provider_media_reservations
        set state=case when state='LINKED' then state else 'INGESTED' end,updated_at=now()
        where id=?
        """,
        reservationId);
  }

  public void markLinked(long reservationId) {
    jdbcTemplate.update(
        """
        update meta_whatsapp_provider_media_reservations
        set state='LINKED',updated_at=now(),completed_at=coalesce(completed_at,now())
        where id=?
        """,
        reservationId);
  }

  public void markFailed(long reservationId) {
    jdbcTemplate.update(
        """
        update meta_whatsapp_provider_media_reservations
        set state=case when state='LINKED' then state else 'FAILED' end,updated_at=now()
        where id=?
        """,
        reservationId);
  }

  public record Reservation(
      long id,
      long connectionId,
      long inboxMessageId,
      String providerMediaId,
      long reservedMediaId,
      String state) {}
}
