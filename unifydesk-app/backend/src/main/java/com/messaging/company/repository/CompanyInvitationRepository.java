package com.messaging.company.repository;

import com.messaging.company.entity.CompanyInvitation;
import com.messaging.company.enums.CompanyInvitationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyInvitationRepository extends JpaRepository<CompanyInvitation, Long> {

  @EntityGraph(attributePaths = {"company", "roles"})
  Optional<CompanyInvitation> findByTokenHash(String tokenHash);

  @EntityGraph(attributePaths = {"company", "roles"})
  Optional<CompanyInvitation> findByIdAndCompanyId(Long id, Long companyId);

  @EntityGraph(attributePaths = {"company", "roles"})
  Optional<CompanyInvitation> findFirstByCompanyIdAndEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
      Long companyId, String email, CompanyInvitationStatus status);

  @EntityGraph(attributePaths = {"company", "roles"})
  List<CompanyInvitation> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

  @EntityGraph(attributePaths = {"company", "roles"})
  List<CompanyInvitation> findByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

  @EntityGraph(attributePaths = {"company", "roles"})
  Optional<CompanyInvitation> findByIdAndEmailIgnoreCase(Long id, String email);

  long countByCompanyIdAndStatusAndExpiresAtAfter(
      Long companyId, CompanyInvitationStatus status, Instant now);

  @Modifying
  @Query(
      value =
          """
          delete from company_invitation_roles cir
          using company_invitations ci
          where cir.invitation_id = ci.id
            and cir.role_id = :roleId
            and ci.status = 'PENDING'
          """,
      nativeQuery = true)
  int deletePendingRoleLinks(@Param("roleId") Long roleId);
}
