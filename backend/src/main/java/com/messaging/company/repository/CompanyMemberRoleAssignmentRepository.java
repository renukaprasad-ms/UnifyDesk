package com.messaging.company.repository;

import com.messaging.company.entity.CompanyMemberRoleAssignment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyMemberRoleAssignmentRepository
    extends JpaRepository<CompanyMemberRoleAssignment, Long> {

  @Query(
      "select a from CompanyMemberRoleAssignment a join fetch a.role r "
          + "where a.companyId = :companyId and a.userId = :userId "
          + "and a.revokedAt is null and (a.expiresAt is null or a.expiresAt > :now)")
  List<CompanyMemberRoleAssignment> findActive(
      @Param("companyId") Long companyId, @Param("userId") Long userId, @Param("now") Instant now);

  Optional<CompanyMemberRoleAssignment> findByCompanyIdAndUserIdAndRoleId(
      Long companyId, Long userId, Long roleId);

  List<CompanyMemberRoleAssignment> findByCompanyIdAndUserIdOrderByCreatedAtDesc(
      Long companyId, Long userId);

  List<CompanyMemberRoleAssignment> findByCompanyIdAndRevokedAtIsNull(Long companyId);

  List<CompanyMemberRoleAssignment> findByRoleIdAndRevokedAtIsNull(Long roleId);

  List<CompanyMemberRoleAssignment> findByUserIdAndRevokedAtIsNull(Long userId);

  long countByRoleIdAndRevokedAtIsNull(Long roleId);
}
