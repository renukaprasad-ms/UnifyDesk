package com.messaging.company.service;

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
  private final CompanyMemberRepository memberRepository;

  @Transactional(readOnly = true)
  public void requireActiveMembership(Long userId, Long companyId) {
    if (!memberRepository.existsByCompanyIdAndUserIdAndStatus(
        companyId, userId, CompanyMemberStatus.ACTIVE)) {
      throw new ForbiddenException("Company access denied");
    }
  }

  public void requirePermission(Long userId, Long companyId, CompanyPermission permission) {
    if (!permissionService.hasPermission(userId, companyId, permission)) {
      throw new ForbiddenException("Company permission denied");
    }
  }
}
