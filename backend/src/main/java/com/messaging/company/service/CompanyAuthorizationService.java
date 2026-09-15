package com.messaging.company.service;

import com.messaging.common.exception.ForbiddenException;
import com.messaging.company.enums.CompanyPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CompanyAuthorizationService {

  private final CompanyPermissionService permissionService;

  public void requirePermission(Long userId, Long companyId, CompanyPermission permission) {
    if (!permissionService.hasPermission(userId, companyId, permission)) {
      throw new ForbiddenException("Company permission denied");
    }
  }
}
