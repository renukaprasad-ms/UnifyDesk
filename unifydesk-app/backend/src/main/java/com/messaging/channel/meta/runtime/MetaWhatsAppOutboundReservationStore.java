package com.messaging.channel.meta.runtime;

import com.messaging.common.id.IdGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MetaWhatsAppOutboundReservationStore {

  private final JdbcTemplate jdbcTemplate;

  public Reservation tryReserve(
      long companyId,
      long connectionId,
      String clientRequestId,
      String messageKind,
      Instant now) {
    long runtimeId = IdGenerator.nextId();
    int inserted =
        jdbcTemplate.update(
            """
            insert into meta_whatsapp_message_runtime
              (id,company_id,connection_id,client_request_id,direction,message_kind,
               detailed_status,status_rank,created_at,updated_at)
            values (?,?,?,?,'OUTBOUND',?,'PENDING',0,?,?)
            on conflict (company_id,connection_id,client_request_id)
              where client_request_id is not null
            do nothing
            """,
            runtimeId,
            companyId,
            connectionId,
            clientRequestId,
            messageKind,
            Timestamp.from(now),
            Timestamp.from(now));
    return new Reservation(runtimeId, inserted == 1);
  }

  public record Reservation(long runtimeId, boolean created) {}
}
