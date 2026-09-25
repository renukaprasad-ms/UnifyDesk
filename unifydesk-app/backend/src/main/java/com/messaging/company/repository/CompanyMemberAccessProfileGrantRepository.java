package com.messaging.company.repository;

import com.messaging.company.entity.CompanyMemberAccessProfileGrant;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyMemberAccessProfileGrantRepository
    extends JpaRepository<CompanyMemberAccessProfileGrant, Long> {

  @Query(
      """
      select grant
      from CompanyMemberAccessProfileGrant grant
      where grant.company.id = :companyId
        and grant.user.id = :userId
        and grant.revokedAt is null
        and (grant.expiresAt is null or grant.expiresAt > :now)
      """)
  List<CompanyMemberAccessProfileGrant> findActive(
      @Param("companyId") Long companyId, @Param("userId") Long userId, @Param("now") Instant now);
}
