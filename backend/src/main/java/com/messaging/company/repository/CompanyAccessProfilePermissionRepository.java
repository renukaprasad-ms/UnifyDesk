package com.messaging.company.repository;

import com.messaging.company.entity.CompanyAccessProfilePermission;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyAccessProfilePermissionRepository
    extends JpaRepository<CompanyAccessProfilePermission, Long> {

  List<CompanyAccessProfilePermission> findByProfileIdIn(Collection<Long> profileIds);
}
