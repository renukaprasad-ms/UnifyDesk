package com.messaging.company.repository;

import com.messaging.company.entity.CompanyCustomRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyCustomRoleRepository extends JpaRepository<CompanyCustomRole, Long> {

  List<CompanyCustomRole> findByCompanyIdOrderByNameAsc(Long companyId);

  Optional<CompanyCustomRole> findByIdAndCompanyId(Long id, Long companyId);

  boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);

  boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);
}
