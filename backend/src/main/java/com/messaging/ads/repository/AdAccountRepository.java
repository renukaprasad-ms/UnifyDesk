package com.messaging.ads.repository;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdAccountRepository extends JpaRepository<AdAccount, Long> {

  List<AdAccount> findByCompanyIdOrderByProviderAscNameAsc(Long companyId);

  Optional<AdAccount> findByIdAndCompanyId(Long id, Long companyId);

  List<AdAccount> findByCompanyIdAndProvider(Long companyId, AdProvider provider);

  List<AdAccount> findByCompanyIdAndConnectionId(Long companyId, Long connectionId);

  Optional<AdAccount> findByCompanyIdAndProviderAndExternalAccountId(
      Long companyId, AdProvider provider, String externalAccountId);
}
