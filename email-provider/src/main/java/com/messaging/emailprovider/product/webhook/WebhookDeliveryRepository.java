package com.messaging.emailprovider.product.webhook;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {
  @Query(value = """
    SELECT id FROM email_webhook_deliveries
    WHERE status IN ('PENDING','RETRY') AND next_attempt_at <= :now
    ORDER BY created_at
    LIMIT 100
    """, nativeQuery = true)
  List<Long> dueIds(@Param("now") Instant now);

  @Modifying @Transactional
  @Query(value = """
    UPDATE email_webhook_deliveries
    SET status='PROCESSING', attempts=attempts+1,
        claimed_at=:now, updated_at=:now
    WHERE id=:id AND status IN ('PENDING','RETRY') AND next_attempt_at<=:now
    """, nativeQuery = true)
  int claim(@Param("id") Long id, @Param("now") Instant now);

  @Modifying @Transactional
  @Query(value = """
    UPDATE email_webhook_deliveries
    SET status='RETRY', next_attempt_at=:now, claimed_at=NULL, updated_at=:now
    WHERE status='PROCESSING' AND claimed_at < :staleBefore
    """, nativeQuery = true)
  int recoverStale(@Param("staleBefore") Instant staleBefore, @Param("now") Instant now);
}
