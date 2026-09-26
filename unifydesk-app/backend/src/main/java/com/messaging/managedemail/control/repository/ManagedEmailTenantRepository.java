package com.messaging.managedemail.control.repository;

import com.messaging.managedemail.control.entity.ManagedEmailTenant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedEmailTenantRepository extends JpaRepository<ManagedEmailTenant, Long> {

  Optional<ManagedEmailTenant> findByCompanyId(Long companyId);

  Optional<ManagedEmailTenant> findByIdAndCompanyId(Long id, Long companyId);
}
