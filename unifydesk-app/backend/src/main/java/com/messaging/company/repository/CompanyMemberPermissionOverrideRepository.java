package com.messaging.company.repository;

import com.messaging.company.entity.CompanyMemberPermissionOverride;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyMemberPermissionOverrideRepository
    extends JpaRepository<CompanyMemberPermissionOverride, Long> {

  @Query(
      """
      select override
      from CompanyMemberPermissionOverride override
      where override.company.id = :companyId
        and override.user.id = :userId
        and override.revokedAt is null
        and (override.expiresAt is null or override.expiresAt > :now)
      """)
  List<CompanyMemberPermissionOverride> findActive(
      @Param("companyId") Long companyId, @Param("userId") Long userId, @Param("now") Instant now);
}
