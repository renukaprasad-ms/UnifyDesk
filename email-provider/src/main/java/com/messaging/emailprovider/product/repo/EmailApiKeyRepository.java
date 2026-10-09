package com.messaging.emailprovider.product.repo;

import com.messaging.emailprovider.product.model.EmailApiKey;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailApiKeyRepository extends JpaRepository<EmailApiKey, Long> {
  Optional<EmailApiKey> findByLookupPrefix(String lookupPrefix);
  Optional<EmailApiKey> findByIdAndProjectId(Long id, Long projectId);
  List<EmailApiKey> findAllByProjectIdOrderByCreatedAtDesc(Long projectId);
}
