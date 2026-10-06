package com.messaging.company.repository;

import com.messaging.company.entity.CompanyMember;
import com.messaging.company.enums.CompanyMemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyMemberRepository extends JpaRepository<CompanyMember, Long> {

  Optional<CompanyMember> findByCompanyIdAndUserIdAndStatus(
      Long companyId, Long userId, CompanyMemberStatus status);

  boolean existsByCompanyIdAndUserIdAndStatus(
      Long companyId, Long userId, CompanyMemberStatus status);

  Optional<CompanyMember> findByCompanyIdAndUserId(Long companyId, Long userId);

  long countByCompanyIdAndStatus(Long companyId, CompanyMemberStatus status);

  @EntityGraph(attributePaths = "user")
  List<CompanyMember> findByCompanyId(Long companyId);

  List<CompanyMember> findByUserIdAndStatus(Long userId, CompanyMemberStatus status);

  long countByUserIdAndStatus(Long userId, CompanyMemberStatus status);

  @EntityGraph(attributePaths = "user")
  List<CompanyMember> findByCompanyIdAndStatus(Long companyId, CompanyMemberStatus status);
}
