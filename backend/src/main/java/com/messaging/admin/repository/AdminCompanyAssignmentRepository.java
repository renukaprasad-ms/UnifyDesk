package com.messaging.admin.repository;

import com.messaging.admin.entity.AdminCompanyAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminCompanyAssignmentRepository
    extends JpaRepository<AdminCompanyAssignment, Long> {

  boolean existsByAdminUserIdAndCompanyId(Long adminUserId, Long companyId);

  Optional<AdminCompanyAssignment> findByAdminUserIdAndCompanyId(Long adminUserId, Long companyId);

  List<AdminCompanyAssignment> findByAdminUserId(Long adminUserId);
}
