package com.messaging.channel.meta.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class MetaWhatsAppPendingOutcomeRecoveryTests {

  @Test
  void stalePendingBecomesUnknownWithoutProviderReplay() {
    MetaWhatsAppRuntimeProperties properties = new MetaWhatsAppRuntimeProperties();
    properties.setPendingOutcomeTimeout(Duration.ofMinutes(2));
    RecoveryJdbcTemplate jdbcTemplate = new RecoveryJdbcTemplate();
    MetaWhatsAppPendingOutcomeRecovery recovery =
        new MetaWhatsAppPendingOutcomeRecovery(
            jdbcTemplate, properties, mock(MetaWhatsAppRealtimePublisher.class));

    assertThat(recovery.recoverIfStale(10L, 20L, "request-1")).isTrue();
    assertThat(jdbcTemplate.updateSql).contains("detailed_status='UNKNOWN'");
    assertThat(jdbcTemplate.queryArgs[0]).isEqualTo(10L);
    assertThat(jdbcTemplate.queryArgs[1]).isEqualTo(20L);
    assertThat(jdbcTemplate.queryArgs[2]).isEqualTo("request-1");
    assertThat((Timestamp) jdbcTemplate.queryArgs[3])
        .isBefore(Timestamp.from(Instant.now().minusSeconds(110)));
  }

  private static final class RecoveryJdbcTemplate extends JdbcTemplate {
    private Object[] queryArgs;
    private String updateSql;

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
      queryArgs = args;
      try {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(1L);
        when(resultSet.getLong("company_id")).thenReturn(10L);
        when(resultSet.getLong("connection_id")).thenReturn(20L);
        when(resultSet.getLong("inbox_message_id")).thenReturn(30L);
        when(resultSet.getLong("conversation_id")).thenReturn(40L);
        return List.of(rowMapper.mapRow(resultSet, 0));
      } catch (java.sql.SQLException exception) {
        throw new IllegalStateException(exception);
      }
    }

    @Override
    public int update(String sql, Object... args) {
      updateSql = sql;
      return 1;
    }
  }
}
