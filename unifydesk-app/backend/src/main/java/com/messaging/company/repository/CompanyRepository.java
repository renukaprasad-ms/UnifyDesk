package com.messaging.company.repository;

import com.messaging.company.entity.Company;
import com.messaging.company.enums.CompanyStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyRepository extends JpaRepository<Company, Long> {

  boolean existsBySlug(String slug);

  Optional<Company> findBySlug(String slug);

  Optional<Company> findByLogoMediaId(Long logoMediaId);

  Page<Company> findByNameContainingIgnoreCaseOrBusinessEmailContainingIgnoreCase(
      String name, String email, Pageable pageable);

  Page<Company> findByStatusNot(CompanyStatus status, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Company c where c.id = :id")
  Optional<Company> findByIdForUpdate(@Param("id") Long id);

  @Query(
      """
      select c
      from Company c
      where c.id > :afterId
        and c.status in :statuses
      order by c.id asc
      """)
  List<Company> findStorageReconciliationBatch(
      @Param("afterId") Long afterId,
      @Param("statuses") Collection<CompanyStatus> statuses,
      Pageable pageable);
}
