package com.messaging.channel.gmail;

import java.sql.PreparedStatement;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GmailSyncPersistenceLock {

  private final JdbcTemplate jdbcTemplate;

  public void lockConnection(long connectionId) {
    jdbcTemplate.execute(
        (ConnectionCallback<Void>)
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement("select pg_advisory_xact_lock(?)")) {
                statement.setLong(1, connectionId);
                statement.execute();
              }
              return null;
            });
  }
}
