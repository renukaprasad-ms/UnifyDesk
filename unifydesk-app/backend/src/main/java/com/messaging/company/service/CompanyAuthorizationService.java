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
    CompanyStatus status = companyStatus(companyId);
    boolean activeMember =
        memberRepository.existsByCompanyIdAndUserIdAndStatus(
            companyId, userId, CompanyMemberStatus.ACTIVE);
    if (activeMember) {
      if (status != CompanyStatus.ACTIVE) {
        throw new ForbiddenException("Company is suspended or unavailable");
      }
      return;
    }
    if (platformCanView(userId, companyId)
        && (status == CompanyStatus.ACTIVE || status == CompanyStatus.SUSPENDED)) {
      return;
    }
    throw new ForbiddenException("Company access denied");
  }

  public void requirePermission(Long userId, Long companyId, CompanyPermission permission) {
    CompanyStatus status = companyStatus(companyId);
    boolean activeMember =
        memberRepository.existsByCompanyIdAndUserIdAndStatus(
            companyId, userId, CompanyMemberStatus.ACTIVE);
    if (!activeMember || status != CompanyStatus.ACTIVE) {
      throw new ForbiddenException("Company permission denied");
    }
    if (!permissionService.hasPermission(userId, companyId, permission)) {
      throw new ForbiddenException("Company permission denied");
    }
  }

  public void requirePermission(Long userId, Long companyId, String permissionKey) {
    CompanyStatus status = companyStatus(companyId);
    boolean activeMember =
        memberRepository.existsByCompanyIdAndUserIdAndStatus(
            companyId, userId, CompanyMemberStatus.ACTIVE);

    if (activeMember) {
      if (status != CompanyStatus.ACTIVE
          || !iamPermissionService.hasPermission(userId, companyId, permissionKey)) {
        throw new ForbiddenException("Company permission denied: " + permissionKey);
      }
      return;
    }

    if ((status == CompanyStatus.ACTIVE || status == CompanyStatus.SUSPENDED)
        && platformCanView(userId, companyId)
        && iamPermissionService.hasPermission(userId, companyId, permissionKey)) {
      return;
    }
    throw new ForbiddenException("Company permission denied: " + permissionKey);
  }

  public boolean hasPermission(Long userId, Long companyId, String permissionKey) {
    CompanyStatus status = companyStatusOrNull(companyId);
    if (status == null) return false;

    boolean activeMember =
        memberRepository.existsByCompanyIdAndUserIdAndStatus(
            companyId, userId, CompanyMemberStatus.ACTIVE);
    if (activeMember) {
      return status == CompanyStatus.ACTIVE
          && iamPermissionService.hasPermission(userId, companyId, permissionKey);
    }
    return (status == CompanyStatus.ACTIVE || status == CompanyStatus.SUSPENDED)
        && platformCanView(userId, companyId)
        && iamPermissionService.hasPermission(userId, companyId, permissionKey);
  }

  private CompanyStatus companyStatus(Long companyId) {
    CompanyStatus status = companyStatusOrNull(companyId);
    if (status == null
        || status == CompanyStatus.DELETION_PENDING
        || status == CompanyStatus.PURGING
        || status == CompanyStatus.PURGED) {
      throw new ForbiddenException("Company is pending deletion or has been purged");
    }
    return status;
  }

  private CompanyStatus companyStatusOrNull(Long companyId) {
    return companyRepository.findById(companyId).map(company -> company.getStatus()).orElse(null);
  }

  private boolean platformCanView(Long userId, Long companyId) {
    return platformAdminAuthorizationService.canAccessCompany(userId, companyId)
        && platformAdminAuthorizationService.hasPermission(userId, PlatformPermission.COMPANY_VIEW);
  }
}
