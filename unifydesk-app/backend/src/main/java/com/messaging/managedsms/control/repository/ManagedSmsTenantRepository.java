package com.messaging.managedsms.control.repository;
import com.messaging.managedsms.control.entity.ManagedSmsTenant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ManagedSmsTenantRepository extends JpaRepository<ManagedSmsTenant, Long> {
  Optional<ManagedSmsTenant> findByCompanyId(Long companyId);
  Optional<ManagedSmsTenant> findByIdAndCompanyId(Long id, Long companyId);
}
