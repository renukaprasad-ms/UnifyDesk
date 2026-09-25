package com.messaging.company.repository;

import com.messaging.company.entity.CompanyRolePermission;
import com.messaging.company.enums.CompanyRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRolePermissionRepository
    extends JpaRepository<CompanyRolePermission, Long> {

  List<CompanyRolePermission> findByCompanyIdAndRole(Long companyId, CompanyRole role);

  List<CompanyRolePermission> findByCompanyIsNullAndRole(CompanyRole role);
}
