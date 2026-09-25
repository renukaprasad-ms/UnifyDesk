package com.messaging.ads.repository;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdProvider;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdAccountRepository extends JpaRepository<AdAccount, Long> {

  List<AdAccount> findByCompanyIdOrderByProviderAscNameAsc(Long companyId);

  Optional<AdAccount> findByIdAndCompanyId(Long id, Long companyId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select account from AdAccount account where account.id = :id and account.companyId ="
          + " :companyId")
  Optional<AdAccount> findByIdAndCompanyIdForUpdate(
      @Param("id") Long id, @Param("companyId") Long companyId);

  List<AdAccount> findByCompanyIdAndProvider(Long companyId, AdProvider provider);

  List<AdAccount> findByProviderAndManagedTrueAndAccessibleTrueOrderByIdAsc(
      AdProvider provider);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select account from AdAccount account where account.companyId = :companyId and"
          + " account.provider = :provider order by account.id")
  List<AdAccount> findByCompanyIdAndProviderForUpdate(
      @Param("companyId") Long companyId, @Param("provider") AdProvider provider);

  List<AdAccount> findByCompanyIdAndConnectionId(Long companyId, Long connectionId);

  Optional<AdAccount> findByCompanyIdAndProviderAndExternalAccountId(
      Long companyId, AdProvider provider, String externalAccountId);
}
