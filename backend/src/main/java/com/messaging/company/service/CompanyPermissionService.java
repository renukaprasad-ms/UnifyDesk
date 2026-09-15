package com.messaging.company.service;

import com.messaging.company.entity.CompanyAccessProfilePermission;
import com.messaging.company.entity.CompanyMember;
import com.messaging.company.entity.CompanyMemberAccessProfileGrant;
import com.messaging.company.entity.CompanyMemberPermissionOverride;
import com.messaging.company.entity.CompanyRolePermission;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.enums.CompanyPermission;
import com.messaging.company.enums.CompanyRole;
import com.messaging.company.enums.PermissionEffect;
import com.messaging.company.repository.CompanyAccessProfilePermissionRepository;
import com.messaging.company.repository.CompanyMemberAccessProfileGrantRepository;
import com.messaging.company.repository.CompanyMemberPermissionOverrideRepository;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.company.repository.CompanyRolePermissionRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyPermissionService {

  private final CompanyMemberRepository memberRepository;
  private final CompanyRolePermissionRepository rolePermissionRepository;
  private final CompanyMemberPermissionOverrideRepository overrideRepository;
  private final CompanyMemberAccessProfileGrantRepository profileGrantRepository;
  private final CompanyAccessProfilePermissionRepository profilePermissionRepository;
  private final CompanyRoleDefaults roleDefaults;

  @Transactional(readOnly = true)
  public boolean hasPermission(Long userId, Long companyId, CompanyPermission permission) {
    return effectivePermissions(userId, companyId).contains(permission);
  }

  @Transactional(readOnly = true)
  public Set<CompanyPermission> effectivePermissions(Long userId, Long companyId) {
    CompanyMember member =
        memberRepository
            .findByCompanyIdAndUserIdAndStatus(companyId, userId, CompanyMemberStatus.ACTIVE)
            .orElse(null);
    if (member == null) {
      return EnumSet.noneOf(CompanyPermission.class);
    }
    if (member.getRole() == CompanyRole.OWNER) {
      return EnumSet.allOf(CompanyPermission.class);
    }

    EnumSet<CompanyPermission> permissions = globalRolePermissions(member.getRole());
    applyCompanyRoleOverrides(companyId, member.getRole(), permissions);
    applyAccessProfiles(companyId, userId, permissions);
    applyUserOverrides(companyId, userId, permissions);
    return permissions;
  }

  private EnumSet<CompanyPermission> globalRolePermissions(CompanyRole role) {
    List<CompanyRolePermission> seededPermissions =
        rolePermissionRepository.findByCompanyIsNullAndRole(role);
    if (seededPermissions.isEmpty()) {
      return EnumSet.copyOf(roleDefaults.permissionsFor(role));
    }

    EnumSet<CompanyPermission> permissions = EnumSet.noneOf(CompanyPermission.class);
    seededPermissions.forEach(
        rolePermission -> {
          if (rolePermission.getEffect() == PermissionEffect.ALLOW) {
            permissions.add(rolePermission.getPermission());
          }
        });
    return permissions;
  }

  private void applyCompanyRoleOverrides(
      Long companyId, CompanyRole role, EnumSet<CompanyPermission> permissions) {
    rolePermissionRepository
        .findByCompanyIdAndRole(companyId, role)
        .forEach(
            rolePermission -> {
              if (rolePermission.getEffect() == PermissionEffect.ALLOW) {
                permissions.add(rolePermission.getPermission());
              } else {
                permissions.remove(rolePermission.getPermission());
              }
            });
  }

  private void applyAccessProfiles(
      Long companyId, Long userId, EnumSet<CompanyPermission> permissions) {
    Instant now = Instant.now();
    List<CompanyMemberAccessProfileGrant> grants =
        profileGrantRepository.findActive(companyId, userId, now);
    if (grants.isEmpty()) {
      return;
    }
    Set<Long> profileIds =
        grants.stream().map(grant -> grant.getProfile().getId()).collect(Collectors.toSet());
    profilePermissionRepository.findByProfileIdIn(profileIds).stream()
        .map(CompanyAccessProfilePermission::getPermission)
        .forEach(permissions::add);
  }

  private void applyUserOverrides(
      Long companyId, Long userId, EnumSet<CompanyPermission> permissions) {
    Collection<CompanyMemberPermissionOverride> activeOverrides = activeOverrides(companyId, userId);
    activeOverrides.stream()
        .filter(override -> override.getEffect() == PermissionEffect.ALLOW)
        .map(CompanyMemberPermissionOverride::getPermission)
        .forEach(permissions::add);
    activeOverrides.stream()
        .filter(override -> override.getEffect() == PermissionEffect.DENY)
        .map(CompanyMemberPermissionOverride::getPermission)
        .forEach(permissions::remove);
  }

  private Collection<CompanyMemberPermissionOverride> activeOverrides(Long companyId, Long userId) {
    Instant now = Instant.now();
    return overrideRepository.findActive(companyId, userId, now);
  }
}
