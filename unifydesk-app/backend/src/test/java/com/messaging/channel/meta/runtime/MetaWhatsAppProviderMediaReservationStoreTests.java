package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class MetaWhatsAppProviderMediaReservationStoreTests {

  @Test
  void retryReusesThePersistedReservedMediaId() {
    ReservationJdbcTemplate jdbcTemplate = new ReservationJdbcTemplate(30L);
    MetaWhatsAppProviderMediaReservationStore store =
        new MetaWhatsAppProviderMediaReservationStore(jdbcTemplate);

    var first = store.reserveOrGet(20L, 30L, "media-1");
    var second = store.reserveOrGet(20L, 30L, "media-1");

    assertThat(first.reservedMediaId()).isEqualTo(99L);
    assertThat(second.reservedMediaId()).isEqualTo(99L);
    assertThat(jdbcTemplate.insertSql).contains("on conflict (connection_id,provider_media_id)");
    assertThat(jdbcTemplate.insertCalls).isEqualTo(2);
  }

  @Test
  void providerMediaCannotBeReboundToAnotherInboxMessage() {
    MetaWhatsAppProviderMediaReservationStore store =
        new MetaWhatsAppProviderMediaReservationStore(new ReservationJdbcTemplate(31L));

    assertThatThrownBy(() -> store.reserveOrGet(20L, 30L, "media-1"))
        .isInstanceOf(MetaWhatsAppRuntimeException.class)
        .satisfies(
            exception ->
                assertThat(((MetaWhatsAppRuntimeException) exception).safeCode())
                    .isEqualTo("WHATSAPP_MEDIA_RESERVATION_CONFLICT"));
  }

  private static final class ReservationJdbcTemplate extends JdbcTemplate {
    private final long persistedInboxMessageId;
    private String insertSql;
    private int insertCalls;

    private ReservationJdbcTemplate(long persistedInboxMessageId) {
      this.persistedInboxMessageId = persistedInboxMessageId;
    }

    @Override
    public int update(String sql, Object... args) {
      if (sql.contains("insert into meta_whatsapp_provider_media_reservations")) {
        insertSql = sql;
        insertCalls++;
      }
      return 1;
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
      try {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(40L);
        when(resultSet.getLong("connection_id")).thenReturn(20L);
        when(resultSet.getLong("inbox_message_id")).thenReturn(persistedInboxMessageId);
        when(resultSet.getString("provider_media_id")).thenReturn("media-1");
        when(resultSet.getLong("reserved_media_id")).thenReturn(99L);
        when(resultSet.getString("state")).thenReturn("RESERVED");
        return List.of(rowMapper.mapRow(resultSet, 0));
      } catch (java.sql.SQLException exception) {
        throw new IllegalStateException(exception);
      }
    }
  }
}
