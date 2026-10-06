package com.messaging.company.service;

import com.messaging.admin.enums.PlatformPermission;
import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.company.entity.CompanyCustomRolePermission;
import com.messaging.company.entity.CompanyMember;
import com.messaging.company.entity.CompanyMemberRoleAssignment;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.enums.CompanyRole;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.repository.CompanyCustomRolePermissionRepository;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.company.repository.CompanyMemberRoleAssignmentRepository;
import com.messaging.company.repository.PermissionDefinitionRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyIamPermissionService {

  private final CompanyMemberRepository memberRepository;
  private final CompanyMemberRoleAssignmentRepository assignmentRepository;
  private final CompanyCustomRolePermissionRepository rolePermissionRepository;
  private final PermissionDefinitionRepository permissionDefinitionRepository;
  private final PlatformAdminAuthorizationService platformAdminAuthorizationService;

  @Transactional(readOnly = true)
  public boolean hasPermission(Long userId, Long companyId, String permissionKey) {
    CompanyMember member = activeMember(userId, companyId);
    if (member != null) {
      if (member.getRole() == CompanyRole.OWNER) {
        return permissionDefinitionRepository.existsById(permissionKey);
      }
      return effectivePermissions(member, companyId, userId).contains(permissionKey);
    }
    return hasPlatformWorkspaceAccess(userId, companyId)
        && platformPermissions(userId).contains(permissionKey);
  }

  @Transactional(readOnly = true)
  public Set<String> effectivePermissions(Long userId, Long companyId) {
    CompanyMember member = activeMember(userId, companyId);
    if (member != null) {
      if (member.getRole() == CompanyRole.OWNER) {
        return allPermissions();
      }
      return Set.copyOf(effectivePermissions(member, companyId, userId));
    }
    if (hasPlatformWorkspaceAccess(userId, companyId)) {
      return platformPermissions(userId);
    }
    return Set.of();
  }

  @Transactional(readOnly = true)
  public boolean isOwner(Long userId, Long companyId) {
    CompanyMember member = activeMember(userId, companyId);
    return member != null && member.getRole() == CompanyRole.OWNER;
  }

  @Transactional(readOnly = true)
  public boolean canDelegate(Long userId, Long companyId, Set<String> permissionKeys) {
    CompanyMember member = activeMember(userId, companyId);
    if (member == null) return false;
    if (member.getRole() == CompanyRole.OWNER) {
      return permissionDefinitionRepository.findAllById(permissionKeys).size()
          == permissionKeys.size();
    }
    return effectivePermissions(userId, companyId).containsAll(permissionKeys);
  }

  private CompanyMember activeMember(Long userId, Long companyId) {
    return memberRepository
        .findByCompanyIdAndUserIdAndStatus(companyId, userId, CompanyMemberStatus.ACTIVE)
        .orElse(null);
  }

  private Set<String> effectivePermissions(CompanyMember member, Long companyId, Long userId) {
    Set<String> permissions = new HashSet<>(CompanyPermissionKeys.defaultsFor(member.getRole()));
    List<CompanyMemberRoleAssignment> assignments =
        assignmentRepository.findActive(companyId, userId, Instant.now());
    if (assignments.isEmpty()) return permissions;

    Set<Long> roleIds = new HashSet<>();
    assignments.forEach(assignment -> roleIds.add(assignment.getRole().getId()));
    rolePermissionRepository.findByRoleIdIn(roleIds).stream()
        .map(CompanyCustomRolePermission::getPermissionKey)
        .forEach(permissions::add);
    return permissions;
  }

  private Set<String> allPermissions() {
    TreeSet<String> all = new TreeSet<>();
    permissionDefinitionRepository.findAll().forEach(permission -> all.add(permission.getKey()));
    return Set.copyOf(all);
  }

  private Set<String> platformPermissions(Long userId) {
    Set<PlatformPermission> platform = platformAdminAuthorizationService.permissions(userId);
    Set<String> permissions = new HashSet<>();
    if (platform.contains(PlatformPermission.COMPANY_VIEW)) {
      permissions.add(CompanyPermissionKeys.COMPANY_READ);
      permissions.add(CompanyPermissionKeys.USER_READ);
      permissions.add(CompanyPermissionKeys.ROLE_READ);
      permissions.add(CompanyPermissionKeys.SETTINGS_READ);
      permissions.add(CompanyPermissionKeys.MEDIA_READ);
    }
    if (platform.contains(PlatformPermission.COMPANY_UPDATE)) {
      permissions.add(CompanyPermissionKeys.COMPANY_MODIFY);
      permissions.add(CompanyPermissionKeys.SETTINGS_MODIFY);
    }
    if (platform.contains(PlatformPermission.BILLING_VIEW)) {
      permissions.add(CompanyPermissionKeys.BILLING_READ);
      permissions.add(CompanyPermissionKeys.BILLING_BALANCE_READ);
      permissions.add(CompanyPermissionKeys.BILLING_CARD_READ);
      permissions.add(CompanyPermissionKeys.BILLING_INVOICE_READ);
      permissions.add(CompanyPermissionKeys.BILLING_INVOICE_DOWNLOAD);
      permissions.add(CompanyPermissionKeys.BILLING_SUBSCRIPTION_READ);
    }
    if (platform.contains(PlatformPermission.BILLING_ADJUST)) {
      permissions.add(CompanyPermissionKeys.BILLING_BALANCE_ADD);
      permissions.add(CompanyPermissionKeys.BILLING_CARD_ADD);
      permissions.add(CompanyPermissionKeys.BILLING_CARD_MODIFY);
      permissions.add(CompanyPermissionKeys.BILLING_CARD_DELETE);
      permissions.add(CompanyPermissionKeys.BILLING_SUBSCRIPTION_MODIFY);
      permissions.add(CompanyPermissionKeys.BILLING_SUBSCRIPTION_CANCEL);
    }
    return Set.copyOf(permissions);
  }

  private boolean hasPlatformWorkspaceAccess(Long userId, Long companyId) {
    return platformAdminAuthorizationService.canAccessCompany(userId, companyId)
        && platformAdminAuthorizationService.hasPermission(userId, PlatformPermission.COMPANY_VIEW);
  }
}
