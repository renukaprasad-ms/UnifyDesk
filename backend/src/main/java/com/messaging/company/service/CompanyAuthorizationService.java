package com.messaging.company.service;

import com.messaging.admin.enums.PlatformPermission;
import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.common.exception.ForbiddenException;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.enums.CompanyPermission;
import com.messaging.company.repository.CompanyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyAuthorizationService {

  private final CompanyPermissionService permissionService;
  private final CompanyIamPermissionService iamPermissionService;
  private final CompanyMemberRepository memberRepository;
  private final PlatformAdminAuthorizationService platformAdminAuthorizationService;

  @Transactional(readOnly = true)
  public void requireActiveMembership(Long userId, Long companyId) {
    boolean member =
        memberRepository.existsByCompanyIdAndUserIdAndStatus(
            companyId, userId, CompanyMemberStatus.ACTIVE);
    if (!member && !hasPlatformWorkspaceAccess(userId, companyId)) {
      throw new ForbiddenException("Company access denied");
    }
  }

  public void requirePermission(Long userId, Long companyId, CompanyPermission permission) {
    if (hasPlatformWorkspaceAccess(userId, companyId)) {
      return;
    }
    if (!permissionService.hasPermission(userId, companyId, permission)) {
      throw new ForbiddenException("Company permission denied");
    }
  }

  public void requirePermission(Long userId, Long companyId, String permissionKey) {
    if (!iamPermissionService.hasPermission(userId, companyId, permissionKey)) {
      throw new ForbiddenException("Company permission denied: " + permissionKey);
    }
  }

  public boolean hasPermission(Long userId, Long companyId, String permissionKey) {
    return iamPermissionService.hasPermission(userId, companyId, permissionKey);
  }

  private boolean hasPlatformWorkspaceAccess(Long userId, Long companyId) {
    return platformAdminAuthorizationService.canAccessCompany(userId, companyId)
        && platformAdminAuthorizationService.hasPermission(userId, PlatformPermission.COMPANY_VIEW);
  }
}
