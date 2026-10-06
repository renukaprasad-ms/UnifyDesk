package com.messaging.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.messaging.admin.enums.PlatformPermission;
import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.company.entity.CompanyCustomRole;
import com.messaging.company.entity.CompanyCustomRolePermission;
import com.messaging.company.entity.CompanyMember;
import com.messaging.company.entity.CompanyMemberRoleAssignment;
import com.messaging.company.entity.PermissionDefinition;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.enums.CompanyRole;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.repository.CompanyCustomRolePermissionRepository;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.company.repository.CompanyMemberRoleAssignmentRepository;
import com.messaging.company.repository.PermissionDefinitionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyIamPermissionServiceTests {

  private static final Long COMPANY_ID = 100L;
  private static final Long USER_ID = 200L;

  @Mock private CompanyMemberRepository memberRepository;
  @Mock private CompanyMemberRoleAssignmentRepository assignmentRepository;
  @Mock private CompanyCustomRolePermissionRepository rolePermissionRepository;
  @Mock private PermissionDefinitionRepository permissionDefinitionRepository;
  @Mock private PlatformAdminAuthorizationService platformAdminAuthorizationService;

  private CompanyIamPermissionService service;

  @BeforeEach
  void setUp() {
    service =
        new CompanyIamPermissionService(
            memberRepository,
            assignmentRepository,
            rolePermissionRepository,
            permissionDefinitionRepository,
            platformAdminAuthorizationService);
  }

  @Test
  void inactiveMemberHasNoPermissions() {
    when(memberRepository.findByCompanyIdAndUserIdAndStatus(
            COMPANY_ID, USER_ID, CompanyMemberStatus.ACTIVE))
        .thenReturn(Optional.empty());

    assertThat(service.effectivePermissions(USER_ID, COMPANY_ID)).isEmpty();
    assertThat(service.hasPermission(USER_ID, COMPANY_ID, CompanyPermissionKeys.EMAIL_READ))
        .isFalse();
  }

  @Test
  void ownerCanUseKnownPermissionButNotUnknownPermission() {
    when(memberRepository.findByCompanyIdAndUserIdAndStatus(
            COMPANY_ID, USER_ID, CompanyMemberStatus.ACTIVE))
        .thenReturn(Optional.of(member(CompanyRole.OWNER)));
    when(permissionDefinitionRepository.existsById(CompanyPermissionKeys.EMAIL_CONNECT))
        .thenReturn(true);
    when(permissionDefinitionRepository.existsById("email.conect")).thenReturn(false);

    assertThat(service.hasPermission(USER_ID, COMPANY_ID, CompanyPermissionKeys.EMAIL_CONNECT))
        .isTrue();
    assertThat(service.hasPermission(USER_ID, COMPANY_ID, "email.conect")).isFalse();
  }

  @Test
  void managerKeepsChannelManagementDefaults() {
    when(memberRepository.findByCompanyIdAndUserIdAndStatus(
            COMPANY_ID, USER_ID, CompanyMemberStatus.ACTIVE))
        .thenReturn(Optional.of(member(CompanyRole.MANAGER)));
    when(assignmentRepository.findActive(any(), any(), any(Instant.class))).thenReturn(List.of());

    assertThat(service.effectivePermissions(USER_ID, COMPANY_ID))
        .contains(
            CompanyPermissionKeys.EMAIL_CONNECT,
            CompanyPermissionKeys.WHATSAPP_CONNECT,
            CompanyPermissionKeys.SMS_CONNECT)
        .doesNotContain(CompanyPermissionKeys.ROLE_CREATE);
  }

  @Test
  void platformCompanyViewDoesNotBecomeTenantWriteAccess() {
    when(memberRepository.findByCompanyIdAndUserIdAndStatus(
            COMPANY_ID, USER_ID, CompanyMemberStatus.ACTIVE))
        .thenReturn(Optional.empty());
    when(platformAdminAuthorizationService.canAccessCompany(USER_ID, COMPANY_ID)).thenReturn(true);
    when(platformAdminAuthorizationService.hasPermission(
            USER_ID, PlatformPermission.COMPANY_VIEW))
        .thenReturn(true);
    when(platformAdminAuthorizationService.permissions(USER_ID))
        .thenReturn(Set.of(PlatformPermission.COMPANY_VIEW));

    assertThat(service.hasPermission(USER_ID, COMPANY_ID, CompanyPermissionKeys.COMPANY_READ))
        .isTrue();
    assertThat(service.hasPermission(USER_ID, COMPANY_ID, CompanyPermissionKeys.COMPANY_MODIFY))
        .isFalse();
    assertThat(service.hasPermission(USER_ID, COMPANY_ID, CompanyPermissionKeys.COMPANY_DELETE))
        .isFalse();
    assertThat(service.canDelegate(
            USER_ID, COMPANY_ID, Set.of(CompanyPermissionKeys.COMPANY_READ)))
        .isFalse();
  }

  @Test
  void customRolesAreUnionedWithBasePermissions() {
    CompanyCustomRole role = new CompanyCustomRole();
    role.setId(900L);

    CompanyMemberRoleAssignment assignment = new CompanyMemberRoleAssignment();
    assignment.setRole(role);

    CompanyCustomRolePermission customPermission = new CompanyCustomRolePermission();
    customPermission.setRole(role);
    customPermission.setPermissionKey(CompanyPermissionKeys.BILLING_CARD_ADD);

    when(memberRepository.findByCompanyIdAndUserIdAndStatus(
            COMPANY_ID, USER_ID, CompanyMemberStatus.ACTIVE))
        .thenReturn(Optional.of(member(CompanyRole.MEMBER)));
    when(assignmentRepository.findActive(any(), any(), any(Instant.class)))
        .thenReturn(List.of(assignment));
    when(rolePermissionRepository.findByRoleIdIn(Set.of(900L)))
        .thenReturn(List.of(customPermission));

    assertThat(service.effectivePermissions(USER_ID, COMPANY_ID))
        .contains(CompanyPermissionKeys.EMAIL_READ, CompanyPermissionKeys.BILLING_CARD_ADD);
  }

  @Test
  void ownerEffectivePermissionsComeFromCatalog() {
    PermissionDefinition read = permission(CompanyPermissionKeys.EMAIL_READ);
    PermissionDefinition send = permission(CompanyPermissionKeys.EMAIL_SEND);
    when(memberRepository.findByCompanyIdAndUserIdAndStatus(
            COMPANY_ID, USER_ID, CompanyMemberStatus.ACTIVE))
        .thenReturn(Optional.of(member(CompanyRole.OWNER)));
    when(permissionDefinitionRepository.findAll()).thenReturn(List.of(read, send));

    assertThat(service.effectivePermissions(USER_ID, COMPANY_ID))
        .containsExactlyInAnyOrder(
            CompanyPermissionKeys.EMAIL_READ, CompanyPermissionKeys.EMAIL_SEND);
  }

  private CompanyMember member(CompanyRole role) {
    CompanyMember member = new CompanyMember();
    member.setRole(role);
    member.setStatus(CompanyMemberStatus.ACTIVE);
    return member;
  }

  private PermissionDefinition permission(String key) {
    PermissionDefinition permission = new PermissionDefinition();
    permission.setKey(key);
    permission.setResource("email");
    permission.setAction("read");
    permission.setDescription(key);
    permission.setAssignable(true);
    return permission;
  }
}
