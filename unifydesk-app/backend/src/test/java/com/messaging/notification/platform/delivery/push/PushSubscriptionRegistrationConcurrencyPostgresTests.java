package com.messaging.notification.platform.delivery.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.messaging.common.credential.CredentialCipher;
import com.messaging.common.exception.TooManyRequestsException;
import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import com.messaging.notification.platform.delivery.model.DeliveryModels.PushSubscription;
import com.messaging.notification.platform.delivery.push.PushSubscriptionService.PushKeys;
import com.messaging.notification.platform.delivery.push.PushSubscriptionService.RegisterPushSubscription;
import com.messaging.notification.platform.delivery.push.PushSubscriptionService.RegistrationResult;
import com.messaging.notification.platform.delivery.repository.NotificationDeliveryRuntimeRepository;
import com.messaging.notification.platform.push.BrowserPushReadiness;
import com.messaging.security.service.CurrentUserService;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class PushSubscriptionRegistrationConcurrencyPostgresTests {

  @Test
  void capMinusOneParallelDistinctRegistrationsAllowExactlyOne() throws Exception {
    String url = System.getenv("NOTIFICATION_TEST_POSTGRES_URL");
    assumeTrue(url != null && !url.isBlank(), "NOTIFICATION_TEST_POSTGRES_URL is not configured");

    DriverManagerDataSource root = new DriverManagerDataSource();
    root.setUrl(url);
    String username = System.getenv("NOTIFICATION_TEST_POSTGRES_USER");
    String password = System.getenv("NOTIFICATION_TEST_POSTGRES_PASSWORD");
    if (username != null && !username.isBlank()) {
      root.setUsername(username);
    }
    if (password != null) {
      root.setPassword(password);
    }

    JdbcTemplate rootJdbc = new JdbcTemplate(root);
    String schema = "notif_r3_" + UUID.randomUUID().toString().replace("-", "");
    rootJdbc.execute("create schema " + schema);
    try {
      DataSource scoped = schemaScoped(root, schema);
      JdbcTemplate jdbc = new JdbcTemplate(scoped);
      createSchema(jdbc);
      jdbc.update("insert into users(id) values (?)", 7L);
      seedActiveSubscriptions(jdbc, 7L, 9);

      AtomicLong ids = new AtomicLong(10000L);
      NotificationDeliveryRuntimeRepository repository =
          new NotificationDeliveryRuntimeRepository(jdbc) {
            @Override
            public PushSubscription insertSubscription(
                long userId,
                String endpointHash,
                String endpointCiphertext,
                String p256dhCiphertext,
                String authCiphertext,
                Instant expirationTime,
                String deviceLabel) {
              long id = ids.incrementAndGet();
              jdbc.update(
                  """
                  insert into notification_push_subscriptions(
                    id,user_id,endpoint_hash,endpoint_ciphertext,p256dh_ciphertext,auth_ciphertext,
                    expiration_time,device_label,status,created_at,updated_at
                  ) values (?,?,?,?,?,?,?,?, 'ACTIVE',now(),now())
                  """,
                  id,
                  userId,
                  endpointHash,
                  endpointCiphertext,
                  p256dhCiphertext,
                  authCiphertext,
                  expirationTime == null
                      ? null
                      : OffsetDateTime.ofInstant(expirationTime, ZoneOffset.UTC),
                  deviceLabel);
              return findSubscriptionForUser(id, userId).orElseThrow();
            }
          };

      CurrentUserService currentUser = mock(CurrentUserService.class);
      when(currentUser.currentUserId()).thenReturn(7L);
      CredentialCipher cipher = mock(CredentialCipher.class);
      when(cipher.encrypt(anyString()))
          .thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
      BrowserPushReadiness readiness = mock(BrowserPushReadiness.class);
      when(readiness.ready()).thenReturn(true);
      NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
      properties.getPush().setVapidPublicKey("public");
      properties.getPush().setVapidPrivateKey("private");
      properties.getPush().setMaxActiveSubscriptions(10);

      PushSubscriptionService service =
          new PushSubscriptionService(repository, currentUser, cipher, properties, readiness);
      TransactionTemplate tx =
          new TransactionTemplate(new DataSourceTransactionManager(scoped));

      CountDownLatch start = new CountDownLatch(1);
      ExecutorService pool = Executors.newFixedThreadPool(2);
      try {
        Future<Object> first = pool.submit(() -> register(tx, service, request("a"), start));
        Future<Object> second = pool.submit(() -> register(tx, service, request("b"), start));
        start.countDown();

        Object firstResult = first.get();
        Object secondResult = second.get();

        long successes =
            java.util.stream.Stream.of(firstResult, secondResult)
                .filter(RegistrationResult.class::isInstance)
                .count();
        long rateLimited =
            java.util.stream.Stream.of(firstResult, secondResult)
                .filter(TooManyRequestsException.class::isInstance)
                .count();

        assertThat(successes).isEqualTo(1);
        assertThat(rateLimited).isEqualTo(1);
        Integer active =
            jdbc.queryForObject(
                """
                select count(*)
                from notification_push_subscriptions
                where user_id=7
                  and status='ACTIVE'
                  and (expiration_time is null or expiration_time > now())
                """,
                Integer.class);
        assertThat(active).isEqualTo(10);
      } finally {
        pool.shutdownNow();
      }
    } finally {
      rootJdbc.execute("drop schema " + schema + " cascade");
    }
  }

  private Object register(
      TransactionTemplate tx,
      PushSubscriptionService service,
      RegisterPushSubscription request,
      CountDownLatch start)
      throws InterruptedException {
    start.await();
    try {
      return tx.execute(status -> service.register(request));
    } catch (RuntimeException exception) {
      return exception;
    }
  }

  private DataSource schemaScoped(DataSource delegate, String schema) {
    return new DelegatingDataSource(delegate) {
      @Override
      public Connection getConnection() throws SQLException {
        Connection connection = super.getConnection();
        try (var statement = connection.createStatement()) {
          statement.execute("set search_path to " + schema);
        }
        return connection;
      }

      @Override
      public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = super.getConnection(username, password);
        try (var statement = connection.createStatement()) {
          statement.execute("set search_path to " + schema);
        }
        return connection;
      }
    };
  }

  private void createSchema(JdbcTemplate jdbc) {
    jdbc.execute("create table users(id bigint primary key)");
    jdbc.execute(
        """
        create table notification_push_subscriptions (
          id bigint primary key,
          user_id bigint not null,
          endpoint_hash varchar(64) not null unique,
          endpoint_ciphertext text not null,
          p256dh_ciphertext text not null,
          auth_ciphertext text not null,
          expiration_time timestamptz null,
          device_label varchar(120) null,
          status varchar(24) not null,
          created_at timestamptz not null,
          last_success_at timestamptz null,
          last_failure_at timestamptz null,
          revoked_at timestamptz null,
          updated_at timestamptz not null
        )
        """);
  }

  private void seedActiveSubscriptions(JdbcTemplate jdbc, long userId, int count) {
    Instant now = Instant.parse("2026-09-27T00:00:00Z");
    OffsetDateTime timestamp = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
    for (int index = 0; index < count; index++) {
      String hash = String.format("%064x", index + 1);
      jdbc.update(
          """
          insert into notification_push_subscriptions(
            id,user_id,endpoint_hash,endpoint_ciphertext,p256dh_ciphertext,auth_ciphertext,
            device_label,status,created_at,updated_at
          ) values (?,?,?,?,?,?,?,'ACTIVE',?,?)
          """,
          1000L + index,
          userId,
          hash,
          "endpoint-" + index,
          "p256dh-" + index,
          "auth-" + index,
          "Browser " + index,
          timestamp,
          timestamp);
    }
  }

  private RegisterPushSubscription request(String suffix) {
    String p256dh =
        Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[65]);
    String auth =
        Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
    return new RegisterPushSubscription(
        "https://push.example/subscription-" + suffix,
        null,
        new PushKeys(p256dh, auth),
        "Chrome");
  }
}
