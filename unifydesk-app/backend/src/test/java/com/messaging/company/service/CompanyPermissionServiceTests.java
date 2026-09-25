package com.messaging.company.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.messaging.company.entity.Company;
import com.messaging.company.entity.CompanyAccessProfile;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyPermissionServiceTests {

  private static final Long COMPANY_ID = 100L;
  private static final Long USER_ID = 200L;

  @Mock private CompanyMemberRepository memberRepository;
  @Mock private CompanyRolePermissionRepository rolePermissionRepository;
  @Mock private CompanyMemberPermissionOverrideRepository overrideRepository;
  @Mock private CompanyMemberAccessProfileGrantRepository profileGrantRepository;
  @Mock private CompanyAccessProfilePermissionRepository profilePermissionRepository;

  private CompanyPermissionService service;

  @BeforeEach
  void setUp() {
    service =
        new CompanyPermissionService(
            memberRepository,
            rolePermissionRepository,
            overrideRepository,
            profileGrantRepository,
            profilePermissionRepository,
            new CompanyRoleDefaults());
  }

  @Test
  void nonMemberHasNoCompanyPermissions() {
    when(memberRepository.findByCompanyIdAndUserIdAndStatus(
            COMPANY_ID, USER_ID, CompanyMemberStatus.ACTIVE))
        .thenReturn(Optional.empty());

    assertThat(service.effectivePermissions(USER_ID, COMPANY_ID)).isEmpty();
  }

  @Test
  void ownerHasAllPermissionsWithoutExtraQueries() {
    when(memberRepository.findByCompanyIdAndUserIdAndStatus(
            COMPANY_ID, USER_ID, CompanyMemberStatus.ACTIVE))
        .thenReturn(Optional.of(member(CompanyRole.OWNER)));

    assertThat(service.effectivePermissions(USER_ID, COMPANY_ID))
        .containsExactlyInAnyOrder(CompanyPermission.values());
  }

  @Test
  void companyRoleOverrideCanRemoveDefaultRolePermission() {
    when(memberRepository.findByCompanyIdAndUserIdAndStatus(
            COMPANY_ID, USER_ID, CompanyMemberStatus.ACTIVE))
        .thenReturn(Optional.of(member(CompanyRole.MANAGER)));
    when(rolePermissionRepository.findByCompanyIdAndRole(COMPANY_ID, CompanyRole.MANAGER))
        .thenReturn(List.of(rolePermission(CompanyPermission.MESSAGE_SEND, PermissionEffect.DENY)));
    when(profileGrantRepository.findActive(any(), any(), any(Instant.class))).thenReturn(List.of());
    when(overrideRepository.findActive(any(), any(), any(Instant.class))).thenReturn(List.of());

    assertThat(service.effectivePermissions(USER_ID, COMPANY_ID))
        .doesNotContain(CompanyPermission.MESSAGE_SEND)
        .contains(CompanyPermission.MEMBER_INVITE);
  }

  @Test
  void accessProfileAddsPermissionsAndMemberDenyOverrideWinsLast() {
    CompanyAccessProfile profile = profile(300L);
    when(memberRepository.findByCompanyIdAndUserIdAndStatus(
            COMPANY_ID, USER_ID, CompanyMemberStatus.ACTIVE))
        .thenReturn(Optional.of(member(CompanyRole.MEMBER)));
    when(rolePermissionRepository.findByCompanyIdAndRole(COMPANY_ID, CompanyRole.MEMBER))
        .thenReturn(List.of());
    when(profileGrantRepository.findActive(any(), any(), any(Instant.class)))
        .thenReturn(List.of(profileGrant(profile)));
    when(profilePermissionRepository.findByProfileIdIn(Set.of(profile.getId())))
        .thenReturn(List.of(profilePermission(profile, CompanyPermission.BILLING_VIEW)));
    when(overrideRepository.findActive(any(), any(), any(Instant.class)))
        .thenReturn(
            List.of(
                memberOverride(CompanyPermission.BILLING_VIEW, PermissionEffect.DENY),
                memberOverride(CompanyPermission.ADS_VIEW, PermissionEffect.ALLOW)));

    assertThat(service.effectivePermissions(USER_ID, COMPANY_ID))
        .contains(CompanyPermission.ADS_VIEW)
        .doesNotContain(CompanyPermission.BILLING_VIEW);
  }

  private CompanyMember member(CompanyRole role) {
    CompanyMember member = new CompanyMember();
    member.setCompany(company());
    member.setRole(role);
    member.setStatus(CompanyMemberStatus.ACTIVE);
    return member;
  }

  private CompanyRolePermission rolePermission(
      CompanyPermission permission, PermissionEffect effect) {
    CompanyRolePermission rolePermission = new CompanyRolePermission();
    rolePermission.setCompany(company());
    rolePermission.setRole(CompanyRole.MANAGER);
    rolePermission.setPermission(permission);
    rolePermission.setEffect(effect);
    return rolePermission;
  }

  private CompanyMemberPermissionOverride memberOverride(
      CompanyPermission permission, PermissionEffect effect) {
    CompanyMemberPermissionOverride override = new CompanyMemberPermissionOverride();
    override.setCompany(company());
    override.setPermission(permission);
    override.setEffect(effect);
    return override;
  }

  private CompanyMemberAccessProfileGrant profileGrant(CompanyAccessProfile profile) {
    CompanyMemberAccessProfileGrant grant = new CompanyMemberAccessProfileGrant();
    grant.setCompany(company());
    grant.setProfile(profile);
    return grant;
  }

  private CompanyAccessProfilePermission profilePermission(
      CompanyAccessProfile profile, CompanyPermission permission) {
    CompanyAccessProfilePermission profilePermission = new CompanyAccessProfilePermission();
    profilePermission.setProfile(profile);
    profilePermission.setPermission(permission);
    return profilePermission;
  }

  private CompanyAccessProfile profile(Long id) {
    CompanyAccessProfile profile = new CompanyAccessProfile();
    profile.setId(id);
    profile.setCompany(company());
    profile.setName("temporary billing");
    return profile;
  }

  private Company company() {
    Company company = new Company();
    company.setId(COMPANY_ID);
    return company;
  }
}
