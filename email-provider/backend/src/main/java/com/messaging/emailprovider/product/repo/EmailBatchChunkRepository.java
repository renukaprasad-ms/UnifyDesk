package com.messaging.emailprovider.product.repo;

import com.messaging.emailprovider.product.model.EmailBatchChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailBatchChunkRepository extends JpaRepository<EmailBatchChunk, Long> {
  List<EmailBatchChunk> findAllByBatchIdOrderByChunkNumber(Long batchId);
}
