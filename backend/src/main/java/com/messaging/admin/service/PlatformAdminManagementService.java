package com.messaging.admin.service;

import com.messaging.admin.dto.*;
import com.messaging.admin.entity.*;
import com.messaging.admin.enums.*;
import com.messaging.admin.repository.*;
import com.messaging.common.exception.*;
import com.messaging.security.service.CurrentUserService;
import com.messaging.user.entity.User;
import com.messaging.user.repository.UserRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformAdminManagementService {
  private final PlatformAdminRepository adminRepository;
  private final PlatformAdminPermissionRepository permissionRepository;
  private final UserRepository userRepository;
  private final CurrentUserService currentUserService;
  private final PlatformAdminAuthorizationService authorizationService;

  @Transactional(readOnly = true)
  public Page<PlatformAdminResponse> list(String search, Pageable pageable) {
    Long actor = currentUserService.currentUserId();
    authorizationService.requirePermission(actor, PlatformPermission.ADMIN_VIEW);
    Page<PlatformAdmin> page =
        search == null || search.isBlank()
            ? adminRepository.findAll(pageable)
            : adminRepository.findByUserEmailContainingIgnoreCase(search.trim(), pageable);
    return page.map(this::response);
  }

  @Transactional
  public PlatformAdminResponse create(CreatePlatformAdminRequest request) {
    Long actor = currentUserService.currentUserId();
    authorizationService.requirePermission(actor, PlatformPermission.ADMIN_CREATE);
    User user =
        userRepository
            .findByEmail(request.email().trim().toLowerCase())
            .orElseThrow(
                () -> new NotFoundException("User must register before becoming a platform admin"));
    PlatformAdmin admin = adminRepository.findByUserId(user.getId()).orElseGet(PlatformAdmin::new);
    admin.setUser(user);
    admin.setRole(PlatformAdminRole.ADMIN);
    admin.setStatus(PlatformAdminStatus.ACTIVE);
    admin = adminRepository.save(admin);
    replacePermissions(admin, request.permissions());
    return response(admin);
  }

  @Transactional
  public PlatformAdminResponse update(Long adminId, UpdatePlatformAdminRequest request) {
    Long actor = currentUserService.currentUserId();
    authorizationService.requirePermission(actor, PlatformPermission.ADMIN_ASSIGN);
    PlatformAdmin admin =
        adminRepository
            .findById(adminId)
            .orElseThrow(() -> new NotFoundException("Platform admin not found"));
    if (admin.getRole() == PlatformAdminRole.SUPER_ADMIN) {
      authorizationService.requireSuperAdmin(actor);
    }
    admin.setStatus(request.status());
    replacePermissions(admin, request.permissions());
    return response(admin);
  }

  private void replacePermissions(PlatformAdmin admin, Set<PlatformPermission> permissions) {
    if (admin.getRole() == PlatformAdminRole.SUPER_ADMIN) return;
    permissionRepository.deleteByPlatformAdminId(admin.getId());
    if (permissions == null || permissions.isEmpty()) return;
    for (PlatformPermission permission : permissions) {
      PlatformAdminPermission item = new PlatformAdminPermission();
      item.setPlatformAdmin(admin);
      item.setPermission(permission);
      permissionRepository.save(item);
    }
  }

  private PlatformAdminResponse response(PlatformAdmin admin) {
    User u = admin.getUser();
    Set<PlatformPermission> permissions =
        admin.getRole() == PlatformAdminRole.SUPER_ADMIN
            ? EnumSet.allOf(PlatformPermission.class)
            : permissionRepository.findByPlatformAdminId(admin.getId()).stream()
                .map(PlatformAdminPermission::getPermission)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    return new PlatformAdminResponse(
        admin.getId().toString(),
        u.getId().toString(),
        u.getName(),
        u.getEmail(),
        admin.getRole(),
        admin.getStatus(),
        permissions);
  }
}
