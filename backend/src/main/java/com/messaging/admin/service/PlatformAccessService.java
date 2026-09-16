package com.messaging.admin.service;

import com.messaging.admin.dto.PlatformAccessResponse;
import com.messaging.admin.enums.PlatformAdminStatus;
import com.messaging.admin.repository.PlatformAdminRepository;
import com.messaging.security.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformAccessService {
  private final CurrentUserService currentUserService;
  private final PlatformAdminRepository adminRepository;
  private final PlatformAdminAuthorizationService authorizationService;

  @Transactional(readOnly = true)
  public PlatformAccessResponse me() {
    Long userId = currentUserService.currentUserId();
    return adminRepository.findByUserIdAndStatus(userId, PlatformAdminStatus.ACTIVE)
        .map(admin -> new PlatformAccessResponse(
            true, admin.getRole(), authorizationService.permissions(userId)))
        .orElseGet(() -> new PlatformAccessResponse(false, null, java.util.Set.of()));
  }
}
