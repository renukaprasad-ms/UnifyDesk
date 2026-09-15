package com.messaging.company.service;

import com.messaging.common.exception.NotFoundException;
import com.messaging.company.dto.AccessProfileResponse;
import com.messaging.company.dto.CompanyPermissionResponse;
import com.messaging.company.dto.CreateAccessProfileRequest;
import com.messaging.company.dto.GrantAccessProfileRequest;
import com.messaging.company.dto.GrantPermissionOverrideRequest;
import com.messaging.company.entity.Company;
import com.messaging.company.entity.CompanyAccessProfile;
import com.messaging.company.entity.CompanyAccessProfilePermission;
import com.messaging.company.entity.CompanyMemberAccessProfileGrant;
import com.messaging.company.entity.CompanyMemberPermissionOverride;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.enums.CompanyPermission;
import com.messaging.company.repository.CompanyAccessProfilePermissionRepository;
import com.messaging.company.repository.CompanyAccessProfileRepository;
import com.messaging.company.repository.CompanyMemberAccessProfileGrantRepository;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.company.repository.CompanyMemberPermissionOverrideRepository;
import com.messaging.security.service.CurrentUserService;
import com.messaging.user.entity.User;
import com.messaging.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyAccessService {

  private final CompanyService companyService;
  private final CompanyAuthorizationService authorizationService;
  private final CompanyPermissionService permissionService;
  private final CompanyAccessProfileRepository profileRepository;
  private final CompanyAccessProfilePermissionRepository profilePermissionRepository;
  private final CompanyMemberRepository memberRepository;
  private final CompanyMemberPermissionOverrideRepository overrideRepository;
  private final CompanyMemberAccessProfileGrantRepository profileGrantRepository;
  private final UserRepository userRepository;
  private final CurrentUserService currentUserService;

  @Transactional(readOnly = true)
  public CompanyPermissionResponse myPermissions(Long companyId) {
    Long userId = currentUserService.currentUserId();
    return new CompanyPermissionResponse(
        companyId.toString(), permissionService.effectivePermissions(userId, companyId));
  }

  @Transactional
  public void grantPermissionOverride(
      Long companyId, Long targetUserId, GrantPermissionOverrideRequest request) {
    Long actorId = currentUserService.currentUserId();
    authorizationService.requirePermission(actorId, companyId, CompanyPermission.MEMBER_ROLE_UPDATE);
    Company company = companyService.getCompany(companyId);
    requireActiveMember(companyId, targetUserId);
    User target =
        userRepository
            .findById(targetUserId)
            .orElseThrow(() -> new NotFoundException("Target user not found"));
    User actor =
        userRepository.findById(actorId).orElseThrow(() -> new NotFoundException("User not found"));

    CompanyMemberPermissionOverride override = new CompanyMemberPermissionOverride();
    override.setCompany(company);
    override.setUser(target);
    override.setPermission(request.permission());
    override.setEffect(request.effect());
    override.setExpiresAt(request.expiresAt());
    override.setReason(request.reason());
    override.setCreatedBy(actor);
    overrideRepository.saveAndFlush(override);
  }

  @Transactional
  public AccessProfileResponse createProfile(Long companyId, CreateAccessProfileRequest request) {
    Long actorId = currentUserService.currentUserId();
    authorizationService.requirePermission(actorId, companyId, CompanyPermission.SETTINGS_MANAGE);
    Company company = companyService.getCompany(companyId);
    User actor =
        userRepository.findById(actorId).orElseThrow(() -> new NotFoundException("User not found"));

    CompanyAccessProfile profile = new CompanyAccessProfile();
    profile.setCompany(company);
    profile.setName(request.name().trim());
    profile.setDescription(request.description());
    profile.setCreatedBy(actor);
    profile = profileRepository.saveAndFlush(profile);

    for (CompanyPermission permission : request.permissions()) {
      CompanyAccessProfilePermission profilePermission = new CompanyAccessProfilePermission();
      profilePermission.setProfile(profile);
      profilePermission.setPermission(permission);
      profilePermissionRepository.save(profilePermission);
    }
    profilePermissionRepository.flush();
    return toProfileResponse(profile, request.permissions());
  }

  @Transactional
  public void grantAccessProfile(
      Long companyId, Long targetUserId, Long profileId, GrantAccessProfileRequest request) {
    Long actorId = currentUserService.currentUserId();
    authorizationService.requirePermission(actorId, companyId, CompanyPermission.MEMBER_ROLE_UPDATE);
    Company company = companyService.getCompany(companyId);
    requireActiveMember(companyId, targetUserId);
    User target =
        userRepository
            .findById(targetUserId)
            .orElseThrow(() -> new NotFoundException("Target user not found"));
    User actor =
        userRepository.findById(actorId).orElseThrow(() -> new NotFoundException("User not found"));
    CompanyAccessProfile profile =
        profileRepository
            .findById(profileId)
            .filter(existing -> existing.getCompany().getId().equals(companyId))
            .orElseThrow(() -> new NotFoundException("Access profile not found"));

    CompanyMemberAccessProfileGrant grant = new CompanyMemberAccessProfileGrant();
    grant.setCompany(company);
    grant.setUser(target);
    grant.setProfile(profile);
    grant.setExpiresAt(request.expiresAt());
    grant.setCreatedBy(actor);
    profileGrantRepository.saveAndFlush(grant);
  }

  @Transactional(readOnly = true)
  public List<AccessProfileResponse> profiles(Long companyId) {
    Long actorId = currentUserService.currentUserId();
    authorizationService.requirePermission(actorId, companyId, CompanyPermission.SETTINGS_VIEW);
    List<CompanyAccessProfile> profiles = profileRepository.findByCompanyId(companyId);
    if (profiles.isEmpty()) {
      return List.of();
    }
    Set<Long> profileIds =
        profiles.stream().map(CompanyAccessProfile::getId).collect(Collectors.toSet());
    Map<Long, Set<CompanyPermission>> permissionsByProfileId =
        profilePermissionRepository.findByProfileIdIn(profileIds).stream()
            .collect(
                Collectors.groupingBy(
                    permission -> permission.getProfile().getId(),
                    Collectors.mapping(
                        CompanyAccessProfilePermission::getPermission, Collectors.toSet())));
    return profiles.stream()
        .map(
            profile ->
                toProfileResponse(
                    profile, permissionsByProfileId.getOrDefault(profile.getId(), Set.of())))
        .toList();
  }

  private AccessProfileResponse toProfileResponse(
      CompanyAccessProfile profile, Set<CompanyPermission> permissions) {
    return new AccessProfileResponse(
        profile.getId().toString(),
        profile.getCompany().getId().toString(),
        profile.getName(),
        profile.getDescription(),
        permissions);
  }

  private void requireActiveMember(Long companyId, Long userId) {
    memberRepository
        .findByCompanyIdAndUserIdAndStatus(companyId, userId, CompanyMemberStatus.ACTIVE)
        .orElseThrow(() -> new NotFoundException("Company member not found"));
  }
}
