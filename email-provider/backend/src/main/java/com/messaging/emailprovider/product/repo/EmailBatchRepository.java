package com.messaging.emailprovider.product.repo;

import com.messaging.emailprovider.product.model.EmailBatch;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailBatchRepository extends JpaRepository<EmailBatch, Long> {
  Optional<EmailBatch> findByProjectIdAndIdempotencyKey(Long projectId, String key);
  Optional<EmailBatch> findByIdAndProjectId(Long id, Long projectId);
}
