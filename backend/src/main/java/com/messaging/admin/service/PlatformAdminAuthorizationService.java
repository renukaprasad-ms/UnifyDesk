package com.messaging.admin.service;

import com.messaging.admin.enums.PlatformAdminRole;
import com.messaging.admin.enums.PlatformAdminStatus;
import com.messaging.admin.enums.PlatformPermission;
import com.messaging.admin.entity.PlatformAdmin;
import com.messaging.admin.repository.PlatformAdminPermissionRepository;
import java.util.EnumSet;
import java.util.Set;
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
  private final PlatformAdminPermissionRepository permissionRepository;

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

  @Transactional(readOnly = true)
  public Set<PlatformPermission> permissions(Long userId) {
    PlatformAdmin admin =
        platformAdminRepository
            .findByUserIdAndStatus(userId, PlatformAdminStatus.ACTIVE)
            .orElse(null);
    if (admin == null) return Set.of();
    if (admin.getRole() == PlatformAdminRole.SUPER_ADMIN) {
      return EnumSet.allOf(PlatformPermission.class);
    }
    EnumSet<PlatformPermission> permissions = EnumSet.noneOf(PlatformPermission.class);
    permissionRepository.findByPlatformAdminId(admin.getId())
        .forEach(item -> permissions.add(item.getPermission()));
    return Set.copyOf(permissions);
  }

  @Transactional(readOnly = true)
  public boolean hasPermission(Long userId, PlatformPermission permission) {
    if (isSuperAdmin(userId)) return true;
    return platformAdminRepository
        .findByUserIdAndStatus(userId, PlatformAdminStatus.ACTIVE)
        .map(admin -> permissionRepository.existsByPlatformAdminIdAndPermission(admin.getId(), permission))
        .orElse(false);
  }

  public void requirePermission(Long userId, PlatformPermission permission) {
    if (!hasPermission(userId, permission)) {
      throw new ForbiddenException("Platform permission denied");
    }
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
