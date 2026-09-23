package com.messaging.ads.repository;

import com.messaging.ads.entity.AdProviderConnection;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdProviderConnectionRepository
    extends JpaRepository<AdProviderConnection, Long> {

  List<AdProviderConnection> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

  Optional<AdProviderConnection> findByIdAndCompanyId(Long id, Long companyId);

  Optional<AdProviderConnection> findByCompanyIdAndProviderAndExternalUserId(
      Long companyId, AdProvider provider, String externalUserId);

  Optional<AdProviderConnection> findByCompanyIdAndProviderAndExternalBusinessId(
      Long companyId, AdProvider provider, String externalBusinessId);

  List<AdProviderConnection> findByCompanyIdAndProviderAndStatus(
      Long companyId, AdProvider provider, AdConnectionStatus status);
}
