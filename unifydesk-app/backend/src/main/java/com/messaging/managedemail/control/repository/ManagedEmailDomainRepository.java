package com.messaging.managedemail.control.repository;

import com.messaging.managedemail.control.entity.ManagedEmailDomain;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.DomainStatus;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.DomainType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagedEmailDomainRepository extends JpaRepository<ManagedEmailDomain, Long> {

  Optional<ManagedEmailDomain> findByIdAndCompanyId(Long id, Long companyId);

  List<ManagedEmailDomain> findByCompanyIdOrderByUpdatedAtDesc(Long companyId);

  long countByCompanyIdAndStatusNotIn(Long companyId, Collection<DomainStatus> statuses);

  boolean existsByNormalizedDomainIgnoreCase(String normalizedDomain);

  List<ManagedEmailDomain> findByStatusOrderByCreatedAtDesc(DomainStatus status);

  @Query(
      """
      select d from ManagedEmailDomain d
      where d.domainType = :type
        and lower(d.normalizedDomain) = lower(:domain)
        and d.status not in :terminalStatuses
      """)
  Optional<ManagedEmailDomain> findLiveClaim(
      @Param("type") DomainType type,
      @Param("domain") String domain,
      @Param("terminalStatuses") Collection<DomainStatus> terminalStatuses);

  @Query(
      """
      select d from ManagedEmailDomain d
      where d.status in :statuses
        and (d.lastProviderSyncAt is null or d.lastProviderSyncAt < :before)
      order by d.updatedAt asc
      """)
  List<ManagedEmailDomain> findVerificationCandidates(
      @Param("statuses") Collection<DomainStatus> statuses,
      @Param("before") Instant before,
      Pageable pageable);
}
