package com.messaging.company.service;

import com.messaging.admin.enums.PlatformPermission;
import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.common.exception.ForbiddenException;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.enums.CompanyPermission;
import com.messaging.company.enums.CompanyStatus;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.company.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyAuthorizationService {

  private final CompanyPermissionService permissionService;
  private final CompanyIamPermissionService iamPermissionService;
  private final CompanyMemberRepository memberRepository;
  private final CompanyRepository companyRepository;
  private final PlatformAdminAuthorizationService platformAdminAuthorizationService;

  @Transactional(readOnly = true)
  public void requireActiveMembership(Long userId, Long companyId) {
    requireWorkspaceAvailable(companyId);
    boolean member =
        memberRepository.existsByCompanyIdAndUserIdAndStatus(
            companyId, userId, CompanyMemberStatus.ACTIVE);
    if (!member && !hasPlatformWorkspaceAccess(userId, companyId)) {
      throw new ForbiddenException("Company access denied");
    }
  }

  public void requirePermission(Long userId, Long companyId, CompanyPermission permission) {
    requireWorkspaceAvailable(companyId);
    if (hasPlatformWorkspaceAccess(userId, companyId)) {
      return;
    }
    if (!permissionService.hasPermission(userId, companyId, permission)) {
      throw new ForbiddenException("Company permission denied");
    }
  }

  public void requirePermission(Long userId, Long companyId, String permissionKey) {
    requireWorkspaceAvailable(companyId);
    if (!iamPermissionService.hasPermission(userId, companyId, permissionKey)) {
      throw new ForbiddenException("Company permission denied: " + permissionKey);
    }
  }

  public boolean hasPermission(Long userId, Long companyId, String permissionKey) {
    if (!workspaceAvailable(companyId)) {
      return false;
    }
    return iamPermissionService.hasPermission(userId, companyId, permissionKey);
  }

  private void requireWorkspaceAvailable(Long companyId) {
    if (!workspaceAvailable(companyId)) {
      throw new ForbiddenException("Company is pending deletion or has been purged");
    }
  }

  private boolean workspaceAvailable(Long companyId) {
    return companyRepository
        .findById(companyId)
        .map(company -> company.getStatus() == CompanyStatus.ACTIVE || company.getStatus() == CompanyStatus.SUSPENDED)
        .orElse(false);
  }

  private boolean hasPlatformWorkspaceAccess(Long userId, Long companyId) {
    return platformAdminAuthorizationService.canAccessCompany(userId, companyId)
        && platformAdminAuthorizationService.hasPermission(userId, PlatformPermission.COMPANY_VIEW);
  }
}
