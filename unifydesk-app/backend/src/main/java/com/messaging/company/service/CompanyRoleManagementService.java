package com.messaging.company.service;

import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.ForbiddenException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.dto.AssignCompanyRoleRequest;
import com.messaging.company.dto.CompanyIamPermissionsResponse;
import com.messaging.company.dto.CompanyMemberRoleAssignmentResponse;
import com.messaging.company.dto.CompanyRoleResponse;
import com.messaging.company.dto.CreateCompanyRoleRequest;
import com.messaging.company.dto.PermissionDefinitionResponse;
import com.messaging.company.dto.UpdateCompanyRoleRequest;
import com.messaging.company.entity.Company;
import com.messaging.company.entity.CompanyCustomRole;
import com.messaging.company.entity.CompanyCustomRolePermission;
import com.messaging.company.entity.CompanyMember;
import com.messaging.company.entity.CompanyMemberRoleAssignment;
import com.messaging.company.entity.PermissionDefinition;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.enums.CompanyRole;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.repository.CompanyCustomRolePermissionRepository;
import com.messaging.company.repository.CompanyCustomRoleRepository;
import com.messaging.company.repository.CompanyInvitationRepository;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.company.repository.CompanyMemberRoleAssignmentRepository;
import com.messaging.company.repository.PermissionDefinitionRepository;
import com.messaging.lifecycle.enums.LifecycleEntityType;
import com.messaging.lifecycle.service.DeletionQueueService;
import com.messaging.security.service.CurrentUserService;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyRoleManagementService {

  private final CompanyService companyService;
  private final CompanyAuthorizationService authorizationService;
  private final CompanyIamPermissionService iamPermissionService;
  private final CompanyAccessRealtimeService accessRealtimeService;
  private final SystemPermissionCatalogService permissionCatalogService;
  private final CompanySystemRoleService companySystemRoleService;
  private final CompanyMemberRepository memberRepository;
  private final CompanyInvitationRepository invitationRepository;
  private final PermissionDefinitionRepository permissionDefinitionRepository;
  private final CompanyCustomRoleRepository roleRepository;
  private final CompanyCustomRolePermissionRepository rolePermissionRepository;
  private final CompanyMemberRoleAssignmentRepository assignmentRepository;
  private final CurrentUserService currentUserService;
  private final DeletionQueueService deletionQueueService;

  @Transactional
  public List<PermissionDefinitionResponse> permissionCatalog(Long companyId) {
    permissionCatalogService.ensureCatalog();
    Long actorId = currentUserService.currentUserId();
    authorizationService.requirePermission(actorId, companyId, CompanyPermissionKeys.ROLE_READ);
    return permissionDefinitionRepository.findAllByOrderByResourceAscActionAsc().stream()
        .map(this::toPermissionResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public CompanyIamPermissionsResponse myPermissions(Long companyId) {
    Long actorId = currentUserService.currentUserId();
    authorizationService.requireActiveMembership(actorId, companyId);
    return new CompanyIamPermissionsResponse(
        companyId,
        iamPermissionService.isOwner(actorId, companyId),
        iamPermissionService.effectivePermissions(actorId, companyId));
  }

  @Transactional
  public CompanyRoleResponse createRole(Long companyId, CreateCompanyRoleRequest request) {
    Long actorId = currentUserService.currentUserId();
    authorizationService.requirePermission(actorId, companyId, CompanyPermissionKeys.ROLE_CREATE);
    Company company = companyService.getCompany(companyId);
    String name = normalizeName(request.name());
    if (roleRepository.existsByCompanyIdAndNameIgnoreCaseAndDeletedAtIsNull(companyId, name)) {
      throw new ConflictException("A role with this name already exists");
    }

    Set<String> permissions = validatePermissionKeys(actorId, companyId, request.permissions());
    CompanyCustomRole role = new CompanyCustomRole();
    role.setCompany(company);
    role.setName(name);
    role.setDescription(normalizeNullable(request.description()));
    role.setSystemRole(false);
    role.setCreatedByUserId(actorId);
    role = roleRepository.saveAndFlush(role);
    replacePermissions(role, permissions);
    return toRoleResponse(role, permissions);
  }

  @Transactional
  public List<CompanyRoleResponse> roles(Long companyId) {
    permissionCatalogService.ensureCatalog();
    Long actorId = currentUserService.currentUserId();
    authorizationService.requirePermission(actorId, companyId, CompanyPermissionKeys.ROLE_READ);
    companySystemRoleService.ensureDefaults(companyId);
    return roleRepository.findByCompanyIdAndDeletedAtIsNullOrderByNameAsc(companyId).stream()
        .map(role -> toRoleResponse(role, permissionKeys(role.getId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public CompanyRoleResponse role(Long companyId, Long roleId) {
    Long actorId = currentUserService.currentUserId();
    authorizationService.requirePermission(actorId, companyId, CompanyPermissionKeys.ROLE_READ);
    CompanyCustomRole role = requireRole(companyId, roleId);
    return toRoleResponse(role, permissionKeys(roleId));
  }

  @Transactional
  public CompanyRoleResponse updateRole(
      Long companyId, Long roleId, UpdateCompanyRoleRequest request) {
    Long actorId = currentUserService.currentUserId();
    authorizationService.requirePermission(actorId, companyId, CompanyPermissionKeys.ROLE_MODIFY);
    CompanyCustomRole role = requireRole(companyId, roleId);
    requireEditable(role);
    requireCanDelegate(actorId, companyId, permissionKeys(roleId));
    List<Long> affectedUserIds = activeAssignmentUserIds(roleId);

    String name = normalizeName(request.name());
    if (roleRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNotAndDeletedAtIsNull(
        companyId, name, roleId)) {
      throw new ConflictException("A role with this name already exists");
    }

    Set<String> permissions = validatePermissionKeys(actorId, companyId, request.permissions());
    role.setName(name);
    role.setDescription(normalizeNullable(request.description()));
    role = roleRepository.saveAndFlush(role);
    replacePermissions(role, permissions);
    affectedUserIds.forEach(
        userId ->
            accessRealtimeService.notifyAfterCommit(
                userId, companyId, "CUSTOM_ROLE_UPDATED", CompanyMemberStatus.ACTIVE));
    return toRoleResponse(role, permissions);
  }

  @Transactional
  public void deleteRole(Long companyId, Long roleId) {
    Long actorId = currentUserService.currentUserId();
    authorizationService.requirePermission(actorId, companyId, CompanyPermissionKeys.ROLE_DELETE);
    CompanyCustomRole role = requireRole(companyId, roleId);
    requireEditable(role);
    Set<String> permissions = permissionKeys(roleId);
    requireCanDelegate(actorId, companyId, permissions);
    List<Long> affectedUserIds = activeAssignmentUserIds(roleId);
    Instant now = Instant.now();
    List<CompanyMemberRoleAssignment> assignments =
        assignmentRepository.findByRoleIdAndRevokedAtIsNull(roleId);
    assignments.forEach(assignment -> assignment.setRevokedAt(now));
    assignmentRepository.saveAll(assignments);

    invitationRepository.deletePendingRoleLinks(roleId);

    role.setDeletedAt(now);
    role.setDeletedByUserId(actorId);
    roleRepository.saveAndFlush(role);

    deletionQueueService.enqueue(LifecycleEntityType.COMPANY_ROLE, roleId, companyId, actorId);
    affectedUserIds.forEach(
        userId ->
            accessRealtimeService.notifyAfterCommit(
                userId, companyId, "CUSTOM_ROLE_DELETED", CompanyMemberStatus.ACTIVE));
  }

  @Transactional
  public CompanyMemberRoleAssignmentResponse assignRole(
      Long companyId, Long targetUserId, Long roleId, AssignCompanyRoleRequest request) {
    Long actorId = currentUserService.currentUserId();
    requireMemberRoleManagementPermissions(actorId, companyId);
    CompanyMember target = requireActiveMember(companyId, targetUserId);
    if (target.getRole() == CompanyRole.OWNER) {
      throw new BadRequestException("Owner has full access and cannot receive custom roles");
    }

    CompanyCustomRole role = requireRole(companyId, roleId);
    if (role.isSystemRole()) {
      throw new ForbiddenException(
          "System roles are managed by company membership and cannot be assigned manually");
    }
    Set<String> permissions = permissionKeys(roleId);
    requireCanDelegate(actorId, companyId, permissions);

    Instant expiresAt = request == null ? null : request.expiresAt();
    if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
      throw new BadRequestException("Role expiry must be in the future");
    }

    CompanyMemberRoleAssignment assignment =
        assignmentRepository
            .findByCompanyIdAndUserIdAndRoleId(companyId, targetUserId, roleId)
            .orElseGet(CompanyMemberRoleAssignment::new);
    assignment.setCompanyId(companyId);
    assignment.setUserId(targetUserId);
    assignment.setRole(role);
    assignment.setAssignedByUserId(actorId);
    assignment.setExpiresAt(expiresAt);
    assignment.setRevokedAt(null);
    assignment = assignmentRepository.saveAndFlush(assignment);
    accessRealtimeService.notifyAfterCommit(
        targetUserId, companyId, "CUSTOM_ROLE_ASSIGNED", CompanyMemberStatus.ACTIVE);
    return toAssignmentResponse(assignment);
  }

  @Transactional
  public void revokeRole(Long companyId, Long targetUserId, Long roleId) {
    Long actorId = currentUserService.currentUserId();
    requireMemberRoleManagementPermissions(actorId, companyId);
    requireActiveMember(companyId, targetUserId);
    requireRole(companyId, roleId);
    requireCanDelegate(actorId, companyId, permissionKeys(roleId));

    CompanyMemberRoleAssignment assignment =
        assignmentRepository
            .findByCompanyIdAndUserIdAndRoleId(companyId, targetUserId, roleId)
            .orElseThrow(() -> new NotFoundException("Role assignment not found"));
    if (assignment.getRevokedAt() == null) {
      assignment.setRevokedAt(Instant.now());
      assignmentRepository.saveAndFlush(assignment);
      accessRealtimeService.notifyAfterCommit(
          targetUserId, companyId, "CUSTOM_ROLE_REVOKED", CompanyMemberStatus.ACTIVE);
    }
  }

  @Transactional(readOnly = true)
  public List<CompanyMemberRoleAssignmentResponse> memberRoles(Long companyId, Long targetUserId) {
    Long actorId = currentUserService.currentUserId();
    if (!actorId.equals(targetUserId)) {
      authorizationService.requirePermission(actorId, companyId, CompanyPermissionKeys.ROLE_READ);
    } else {
      authorizationService.requireActiveMembership(actorId, companyId);
    }
    requireActiveMember(companyId, targetUserId);
    return assignmentRepository
        .findByCompanyIdAndUserIdOrderByCreatedAtDesc(companyId, targetUserId)
        .stream()
        .map(this::toAssignmentResponse)
        .toList();
  }

  private List<Long> activeAssignmentUserIds(Long roleId) {
    Instant now = Instant.now();
    return assignmentRepository.findByRoleIdAndRevokedAtIsNull(roleId).stream()
        .filter(assignment -> assignment.getExpiresAt() == null || assignment.getExpiresAt().isAfter(now))
        .map(CompanyMemberRoleAssignment::getUserId)
        .distinct()
        .toList();
  }

  private Set<String> validatePermissionKeys(
      Long actorId, Long companyId, Set<String> requestedPermissionKeys) {
    Set<String> requested = new LinkedHashSet<>();
    if (requestedPermissionKeys != null) {
      requestedPermissionKeys.stream().map(this::normalizePermissionKey).forEach(requested::add);
    }
    if (requested.isEmpty()) {
      return Set.of();
    }

    List<PermissionDefinition> definitions = permissionDefinitionRepository.findAllById(requested);
    Set<String> found = new HashSet<>();
    definitions.forEach(definition -> found.add(definition.getKey()));
    Set<String> missing = new TreeSet<>(requested);
    missing.removeAll(found);
    if (!missing.isEmpty()) {
      throw new BadRequestException("Unknown permissions: " + String.join(", ", missing));
    }

    Set<String> nonAssignable = new TreeSet<>();
    definitions.stream()
        .filter(definition -> !definition.isAssignable())
        .map(PermissionDefinition::getKey)
        .forEach(nonAssignable::add);
    if (!nonAssignable.isEmpty()) {
      throw new ForbiddenException(
          "These permissions are owner-only and cannot be assigned: "
              + String.join(", ", nonAssignable));
    }

    requireCanDelegate(actorId, companyId, requested);
    return Set.copyOf(requested);
  }

  private void requireMemberRoleManagementPermissions(Long actorId, Long companyId) {
    authorizationService.requirePermission(actorId, companyId, CompanyPermissionKeys.USER_MODIFY);
    authorizationService.requirePermission(actorId, companyId, CompanyPermissionKeys.ROLE_READ);
    authorizationService.requirePermission(actorId, companyId, CompanyPermissionKeys.ROLE_ASSIGN);
  }

  private void requireCanDelegate(Long actorId, Long companyId, Set<String> permissions) {
    if (!iamPermissionService.canDelegate(actorId, companyId, permissions)) {
      throw new ForbiddenException(
          "You cannot grant or manage permissions that you do not possess");
    }
  }

  private void replacePermissions(CompanyCustomRole role, Set<String> permissions) {
    rolePermissionRepository.deleteByRoleId(role.getId());
    rolePermissionRepository.flush();
    for (String permissionKey : permissions) {
      CompanyCustomRolePermission rolePermission = new CompanyCustomRolePermission();
      rolePermission.setRole(role);
      rolePermission.setPermissionKey(permissionKey);
      rolePermissionRepository.save(rolePermission);
    }
    rolePermissionRepository.flush();
  }

  private Set<String> permissionKeys(Long roleId) {
    TreeSet<String> permissions = new TreeSet<>();
    rolePermissionRepository.findByRoleId(roleId).stream()
        .map(CompanyCustomRolePermission::getPermissionKey)
        .forEach(permissions::add);
    return Set.copyOf(permissions);
  }

  private CompanyCustomRole requireRole(Long companyId, Long roleId) {
    return roleRepository
        .findByIdAndCompanyIdAndDeletedAtIsNull(roleId, companyId)
        .orElseThrow(() -> new NotFoundException("Company role not found"));
  }

  private CompanyMember requireActiveMember(Long companyId, Long userId) {
    return memberRepository
        .findByCompanyIdAndUserIdAndStatus(companyId, userId, CompanyMemberStatus.ACTIVE)
        .orElseThrow(() -> new NotFoundException("Company member not found"));
  }

  private void requireEditable(CompanyCustomRole role) {
    if (role.isSystemRole()) {
      throw new ForbiddenException("System roles cannot be modified or deleted");
    }
  }

  private CompanyRoleResponse toRoleResponse(CompanyCustomRole role, Set<String> permissions) {
    return new CompanyRoleResponse(
        role.getId().toString(),
        role.getCompany().getId().toString(),
        role.getName(),
        role.getDescription(),
        role.isSystemRole(),
        permissions,
        role.getCreatedAt(),
        role.getUpdatedAt());
  }

  private PermissionDefinitionResponse toPermissionResponse(PermissionDefinition permission) {
    return new PermissionDefinitionResponse(
        permission.getKey(),
        permission.getResource(),
        permission.getAction(),
        permission.getDescription(),
        permission.isAssignable());
  }

  private CompanyMemberRoleAssignmentResponse toAssignmentResponse(
      CompanyMemberRoleAssignment assignment) {
    return new CompanyMemberRoleAssignmentResponse(
        assignment.getRole().getId().toString(),
        assignment.getRole().getName(),
        assignment.getExpiresAt(),
        assignment.getRevokedAt(),
        assignment.getCreatedAt());
  }

  private String normalizeName(String value) {
    return value.trim().replaceAll("\\s+", " ");
  }

  private String normalizeNullable(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private String normalizePermissionKey(String value) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException("Permission key cannot be blank");
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
