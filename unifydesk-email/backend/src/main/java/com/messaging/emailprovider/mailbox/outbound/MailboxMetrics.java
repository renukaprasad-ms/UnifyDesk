package com.messaging.emailprovider.mailbox.outbound;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Content-free aggregate gauges. A failed collection has its own alertable signal. */
@Component
@ConditionalOnProperty(prefix = "app.mail.ops", name = "enabled", havingValue = "true")
public class MailboxMetrics {
  private final MailboxOperationsService operations;
  private final JdbcTemplate jdbc;
  private final Path root;
  private final AtomicLong unknown = new AtomicLong(),
      pending = new AtomicLong(),
      quarantined = new AtomicLong();
  private final AtomicLong free = new AtomicLong(),
      oldest = new AtomicLong(),
      ok = new AtomicLong();

  public MailboxMetrics(
      MeterRegistry registry,
      MailboxOperationsService operations,
      JdbcTemplate jdbc,
      @Value("${app.mail.inbound.spool-path:}") String path) {
    this.operations = operations;
    this.jdbc = jdbc;
    this.root = Path.of(path);
    Gauge.builder("mail.outbound.unknown", unknown, AtomicLong::get).register(registry);
    Gauge.builder("mail.inbound.pending", pending, AtomicLong::get).register(registry);
    Gauge.builder("mail.inbound.quarantined", quarantined, AtomicLong::get).register(registry);
    Gauge.builder("mail.storage.free.bytes", free, AtomicLong::get).register(registry);
    Gauge.builder("mail.inbound.oldest.pending.seconds", oldest, AtomicLong::get)
        .register(registry);
    Gauge.builder("mail.ops.collection.ok", ok, AtomicLong::get).register(registry);
  }

  @Scheduled(fixedDelayString = "${app.mail.ops.metrics-interval-ms:30000}")
  public void collect() {
    try {
      var snapshot = operations.snapshot();
      Double age =
          jdbc.queryForObject(
              "SELECT COALESCE(EXTRACT(EPOCH FROM now()-min(received_at)),0) FROM email_mail_inbound_jobs WHERE status IN ('PENDING_SCAN','SCANNING')",
              Double.class);
      long available = Files.getFileStore(root).getUsableSpace();
      unknown.set(snapshot.smtpUnknownAll());
      pending.set(snapshot.inboundPendingScan());
      quarantined.set(snapshot.inboundQuarantinedAll());
      free.set(available);
      oldest.set(age == null ? 0 : Math.max(0, age.longValue()));
      ok.set(1);
    } catch (Exception failure) {
      ok.set(0);
    }
  }
}
