package com.messaging.admin.service;

import com.messaging.admin.enums.PlatformAdminRole;
import com.messaging.admin.enums.PlatformAdminStatus;
import com.messaging.admin.repository.AdminCompanyAssignmentRepository;
import com.messaging.admin.repository.PlatformAdminRepository;
import com.messaging.common.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformAdminAuthorizationService {

  private final PlatformAdminRepository platformAdminRepository;
  private final AdminCompanyAssignmentRepository assignmentRepository;

  @Transactional(readOnly = true)
  public boolean isSuperAdmin(Long userId) {
    return platformAdminRepository
        .findByUserIdAndStatus(userId, PlatformAdminStatus.ACTIVE)
        .filter(admin -> admin.getRole() == PlatformAdminRole.SUPER_ADMIN)
        .isPresent();
  }

  @Transactional(readOnly = true)
  public boolean canAccessCompany(Long userId, Long companyId) {
    if (isSuperAdmin(userId)) {
      return true;
    }
    return assignmentRepository.existsByAdminUserIdAndCompanyId(userId, companyId);
  }

  public void requireSuperAdmin(Long userId) {
    if (!isSuperAdmin(userId)) {
      throw new ForbiddenException("Super admin access required");
    }
  }

  public void requireCompanyAccess(Long userId, Long companyId) {
    if (!canAccessCompany(userId, companyId)) {
      throw new ForbiddenException("Admin company access denied");
    }
  }
}
