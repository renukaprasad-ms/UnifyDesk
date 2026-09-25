package com.messaging.company.repository;

import com.messaging.company.entity.CompanyCustomRolePermission;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyCustomRolePermissionRepository
    extends JpaRepository<CompanyCustomRolePermission, Long> {

  List<CompanyCustomRolePermission> findByRoleId(Long roleId);

  List<CompanyCustomRolePermission> findByRoleIdIn(Collection<Long> roleIds);

  void deleteByRoleId(Long roleId);
}
