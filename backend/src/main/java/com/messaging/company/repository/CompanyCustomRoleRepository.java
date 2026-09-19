package com.messaging.company.repository;

import com.messaging.company.entity.CompanyCustomRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyCustomRoleRepository extends JpaRepository<CompanyCustomRole, Long> {

  List<CompanyCustomRole> findByCompanyIdAndDeletedAtIsNullOrderByNameAsc(Long companyId);

  List<CompanyCustomRole> findByCompanyIdAndDeletedAtIsNull(Long companyId);

  Optional<CompanyCustomRole> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

  boolean existsByCompanyIdAndNameIgnoreCaseAndDeletedAtIsNull(Long companyId, String name);

  boolean existsByCompanyIdAndNameIgnoreCaseAndIdNotAndDeletedAtIsNull(
      Long companyId, String name, Long id);
}
