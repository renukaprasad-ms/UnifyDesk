package com.messaging.company.service;

import com.messaging.billing.service.BillingCatalogService;
import com.messaging.billing.service.DefaultPlanAssignmentService;
import com.messaging.billing.service.EntitlementService;
import com.messaging.channel.dto.ChannelConnectionResponse;
import com.messaging.channel.service.ChannelConnectionService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.common.exception.ForbiddenException;
import com.messaging.common.id.IdGenerator;
import com.messaging.company.dto.CompanyResponse;
import com.messaging.company.dto.CompanyUserResponse;
import com.messaging.company.dto.CreateCompanyRequest;
import com.messaging.company.dto.UpdateCompanyRequest;
import com.messaging.company.entity.Company;
import com.messaging.company.entity.CompanyMember;
import com.messaging.company.entity.CompanyPreference;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.enums.CompanyRole;
import com.messaging.company.enums.CompanyStatus;
import com.messaging.company.repository.CompanyCustomRoleRepository;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.company.repository.CompanyMemberRoleAssignmentRepository;
import com.messaging.company.repository.CompanyPreferenceRepository;
import com.messaging.company.repository.CompanyRepository;
import com.messaging.lifecycle.config.DeletionLifecycleProperties;
import com.messaging.lifecycle.enums.LifecycleEntityType;
import com.messaging.lifecycle.service.DeletionPurgeService;
import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaPurpose;
import com.messaging.media.service.MediaService;
import com.messaging.notification.platform.core.event.NotificationEventRecorder;
import com.messaging.security.service.CurrentUserService;
import com.messaging.user.entity.User;
import com.messaging.user.repository.UserRepository;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyService {

  public static final String DEFAULT_BRAND_COLOR = "#2563EB";

  private final CompanyRepository companyRepository;
  private final CompanyMemberRepository memberRepository;
  private final CompanyMemberRoleAssignmentRepository roleAssignmentRepository;
  private final CompanyCustomRoleRepository customRoleRepository;
  private final CompanyPreferenceRepository preferenceRepository;
  private final UserRepository userRepository;
  private final CurrentUserService currentUserService;
  private final MediaService mediaService;
  private final DefaultPlanAssignmentService defaultPlanAssignmentService;
  private final CompanySystemRoleService companySystemRoleService;
  private final BillingCatalogService billingCatalogService;
  private final EntitlementService entitlementService;
  private final CompanyIamPermissionService iamPermissionService;
  private final CompanyAccessRealtimeService accessRealtimeService;
  private final PasswordEncoder passwordEncoder;
  private final ChannelConnectionService channelConnectionService;
  private final DeletionPurgeService deletionPurgeService;
  private final DeletionLifecycleProperties deletionProperties;
  private final NotificationEventRecorder notificationEventRecorder;

  @Value("${app.company.max-owned-workspaces:3}")
  private int maxOwnedWorkspaces;

  @Transactional
  public CompanyResponse create(CreateCompanyRequest request) {
    Long userId = currentUserService.currentUserId();
    User user =
        userRepository
            .findByIdForUpdate(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));
    assertWorkspaceCreationAvailable(userId);

    Company company = new Company();
    long companyId = IdGenerator.nextId();
    company.setId(companyId);
    company.setName(request.name().trim());
    company.setSlug(slugify(request.name()) + "-" + Long.toUnsignedString(companyId, 36));
    company.setBusinessEmail(normalizeOptional(request.businessEmail()));
    company.setCountry(normalizeCountry(request.country()));
    company.setTimezone(normalizeTimezone(request.timezone()));
    company.setLogoMediaId(resolveLogoMediaId(request.logoMediaId(), userId));
    company.setStatus(CompanyStatus.ACTIVE);
    company.setCreatedBy(user);
    company = companyRepository.saveAndFlush(company);

    CompanyPreference preference = new CompanyPreference();
    preference.setCompanyId(company.getId());
    preference.setBrandColor(normalizeBrandColor(request.brandColor()));
    preferenceRepository.saveAndFlush(preference);

    CompanyMember owner = new CompanyMember();
    owner.setCompany(company);
    owner.setUser(user);
    owner.setRole(CompanyRole.OWNER);
    owner.setStatus(CompanyMemberStatus.ACTIVE);
    memberRepository.saveAndFlush(owner);

    companySystemRoleService.ensureDefaults(company.getId(), userId);
    defaultPlanAssignmentService.assignToNewCompany(company);
    if (company.getLogoMediaId() != null) {
      mediaService.assignCompanyMedia(company.getLogoMediaId(), company.getId(), userId);
    }
    notificationEventRecorder.recordCompanyCreated(company.getId(), userId, company.getName());
    return toResponse(company, CompanyRole.OWNER);
  }

  @Transactional(readOnly = true)
  public CompanyResponse companyForUser(Long companyId, Long userId) {
    Company company = getCompany(companyId);
    CompanyRole role =
        memberRepository
            .findByCompanyIdAndUserIdAndStatus(companyId, userId, CompanyMemberStatus.ACTIVE)
            .map(CompanyMember::getRole)
            .orElse(null);
    return toResponse(company, role);
  }

  @Transactional
  public CompanyResponse update(Long companyId, Long userId, UpdateCompanyRequest request) {
    Company company = getCompany(companyId);
    Long previousLogoMediaId = company.getLogoMediaId();
    Long nextLogoMediaId =
        resolveUpdatedLogoMediaId(request.logoMediaId(), previousLogoMediaId, userId);

    company.setName(request.name().trim());
    company.setBusinessEmail(normalizeOptional(request.businessEmail()));
    company.setCountry(normalizeCountry(request.country()));
    company.setTimezone(normalizeTimezone(request.timezone()));
    company.setLogoMediaId(nextLogoMediaId);
    company = companyRepository.saveAndFlush(company);

    if (request.brandColor() != null && !request.brandColor().isBlank()) {
      CompanyPreference preference =
          preferenceRepository.findById(companyId).orElseGet(CompanyPreference::new);
      preference.setCompanyId(companyId);
      preference.setBrandColor(normalizeBrandColor(request.brandColor()));
      preferenceRepository.saveAndFlush(preference);
    }

    if (nextLogoMediaId != null && !Objects.equals(previousLogoMediaId, nextLogoMediaId)) {
      mediaService.assignCompanyMedia(nextLogoMediaId, company.getId(), userId);
    }
    if (previousLogoMediaId != null && !Objects.equals(previousLogoMediaId, nextLogoMediaId)) {
      mediaService.retireCompanyLogo(previousLogoMediaId, company.getId());
    }

    CompanyRole role =
        memberRepository
            .findByCompanyIdAndUserIdAndStatus(companyId, userId, CompanyMemberStatus.ACTIVE)
            .map(CompanyMember::getRole)
            .orElse(null);
    return toResponse(company, role);
  }

  @Transactional
  public void delete(Long companyId, Long actorUserId) {
    Company company = lockForUpdate(companyId);
    if (company.getStatus() == CompanyStatus.DELETION_PENDING
        || company.getStatus() == CompanyStatus.PURGING
        || company.getStatus() == CompanyStatus.PURGED) {
      return;
    }

    Instant now = Instant.now();
    Instant purgeAfter = now.plus(deletionProperties.getRetention());

    List<ChannelConnectionResponse> connections = channelConnectionService.list(companyId, null);
    for (ChannelConnectionResponse connection : connections) {
      channelConnectionService.disconnect(companyId, Long.valueOf(connection.id()), actorUserId);
    }

    billingCatalogService.cancelForCompanyDeletion(companyId);

    company.setStatus(CompanyStatus.DELETION_PENDING);
    company.setDeletedAt(now);
    company.setPurgeAfter(purgeAfter);
    company.setDeletionRequestedBy(actorUserId);
    company.setPurgeStartedAt(null);
    company.setPurgedAt(null);
    companyRepository.saveAndFlush(company);

    deletionPurgeService.schedule(
        LifecycleEntityType.COMPANY, companyId, companyId, actorUserId, purgeAfter);
  }

  @Transactional(readOnly = true)
  public List<CompanyResponse> myCompanies() {
    Long userId = currentUserService.currentUserId();
    return companiesForUser(userId);
  }

  @Transactional(readOnly = true)
  public List<CompanyResponse> companiesForUser(Long userId) {
    return memberRepository.findByUserIdAndStatus(userId, CompanyMemberStatus.ACTIVE).stream()
        .filter(
            member ->
                member.getCompany().getStatus() == CompanyStatus.ACTIVE
                    || member.getCompany().getStatus() == CompanyStatus.SUSPENDED)
        .map(member -> toResponse(member.getCompany(), member.getRole()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<CompanyUserResponse> companyUsers(Long companyId) {
    getCompany(companyId);
    return memberRepository.findByCompanyId(companyId).stream()
        .filter(member -> member.getStatus() != CompanyMemberStatus.LEFT)
        .map(this::toCompanyUserResponse)
        .toList();
  }

  @Transactional
  public CompanyUserResponse updateMemberRole(
      Long companyId, Long actorUserId, Long targetUserId, CompanyRole role) {
    if (actorUserId.equals(targetUserId)) {
      throw new BadRequestException("Use another owner or manager to change your base role");
    }
    if (role == CompanyRole.OWNER) {
      throw new BadRequestException("Use ownership transfer to make a member the company owner");
    }
    if (!iamPermissionService.canDelegate(
        actorUserId, companyId, com.messaging.company.iam.CompanyPermissionKeys.defaultsFor(role))) {
      throw new BadRequestException(
          "You cannot assign a base role with permissions you do not possess");
    }
    CompanyMember member = requireMembership(companyId, targetUserId);
    if (member.getRole() == CompanyRole.OWNER) {
      throw new BadRequestException("The company owner role can only change through ownership transfer");
    }
    if (member.getStatus() != CompanyMemberStatus.ACTIVE) {
      throw new BadRequestException("Only active members can change base role");
    }
    member.setRole(role);
    memberRepository.saveAndFlush(member);
    accessRealtimeService.notifyAfterCommit(
        targetUserId, companyId, "BASE_ROLE_CHANGED", CompanyMemberStatus.ACTIVE);
    return toCompanyUserResponse(member);
  }

  @Transactional
  public CompanyUserResponse updateMemberStatus(
      Long companyId, Long actorUserId, Long targetUserId, CompanyMemberStatus status) {
    if (actorUserId.equals(targetUserId)) {
      throw new BadRequestException("Use Leave workspace to remove your own access");
    }
    if (status != CompanyMemberStatus.ACTIVE && status != CompanyMemberStatus.SUSPENDED) {
      throw new BadRequestException("Member status can only be ACTIVE or SUSPENDED here");
    }
    lockForUpdate(companyId);
    CompanyMember member = requireMembership(companyId, targetUserId);
    if (member.getRole() == CompanyRole.OWNER) {
      throw new BadRequestException("The company owner cannot be suspended");
    }
    if (member.getStatus() == CompanyMemberStatus.LEFT) {
      throw new BadRequestException("A member who left must be invited again");
    }
    if (status == CompanyMemberStatus.ACTIVE
        && member.getStatus() != CompanyMemberStatus.ACTIVE) {
      long activeMembers =
          memberRepository.countByCompanyIdAndStatus(companyId, CompanyMemberStatus.ACTIVE);
      entitlementService.assertWithinLimit(companyId, "MAX_WORKSPACE_USERS", activeMembers);
    }
    member.setStatus(status);
    memberRepository.saveAndFlush(member);
    accessRealtimeService.notifyAfterCommit(
        targetUserId, companyId, "MEMBERSHIP_STATUS_CHANGED", status);
    return toCompanyUserResponse(member);
  }

  @Transactional
  public void removeMember(Long companyId, Long actorUserId, Long targetUserId) {
    if (actorUserId.equals(targetUserId)) {
      throw new BadRequestException("Use Leave workspace to remove your own access");
    }
    CompanyMember member = requireMembership(companyId, targetUserId);
    if (member.getRole() == CompanyRole.OWNER) {
      throw new BadRequestException("Transfer ownership before removing the company owner");
    }
    leaveMembership(member);
    accessRealtimeService.notifyAfterCommit(
        targetUserId, companyId, "MEMBER_REMOVED", CompanyMemberStatus.LEFT);
  }

  @Transactional
  public void leave(Long companyId, Long userId) {
    CompanyMember member = requireMembership(companyId, userId);
    if (member.getRole() == CompanyRole.OWNER) {
      throw new BadRequestException("Transfer ownership before leaving this company");
    }
    leaveMembership(member);
    accessRealtimeService.notifyAfterCommit(
        userId, companyId, "MEMBER_LEFT", CompanyMemberStatus.LEFT);
  }

  @Transactional
  public void transferOwnership(
      Long companyId, Long actorUserId, Long targetUserId, String currentPassword) {
    lockForUpdate(companyId);
    CompanyMember actor =
        memberRepository
            .findByCompanyIdAndUserIdAndStatus(companyId, actorUserId, CompanyMemberStatus.ACTIVE)
            .orElseThrow(() -> new BadRequestException("Active owner membership is required"));
    if (actor.getRole() != CompanyRole.OWNER) {
      throw new BadRequestException("Only the company owner can transfer ownership");
    }
    if (!passwordEncoder.matches(currentPassword, actor.getUser().getPassword())) {
      throw new ForbiddenException("Current password is incorrect");
    }
    if (actorUserId.equals(targetUserId)) {
      throw new BadRequestException("Choose another active company member");
    }

    CompanyMember target =
        memberRepository
            .findByCompanyIdAndUserIdAndStatus(companyId, targetUserId, CompanyMemberStatus.ACTIVE)
            .orElseThrow(() -> new BadRequestException("Target must be an active company member"));

    Instant now = Instant.now();
    var targetAssignments = roleAssignmentRepository.findActive(companyId, targetUserId, now);
    targetAssignments.forEach(assignment -> assignment.setRevokedAt(now));
    roleAssignmentRepository.saveAll(targetAssignments);

    target.setRole(CompanyRole.OWNER);
    actor.setRole(CompanyRole.MANAGER);
    memberRepository.saveAll(List.of(target, actor));
    memberRepository.flush();
    accessRealtimeService.notifyAfterCommit(
        actorUserId, companyId, "OWNERSHIP_TRANSFERRED", CompanyMemberStatus.ACTIVE);
    accessRealtimeService.notifyAfterCommit(
        targetUserId, companyId, "OWNERSHIP_TRANSFERRED", CompanyMemberStatus.ACTIVE);
  }

  @Transactional(readOnly = true)
  public long companyCountForUser(Long userId) {
    return memberRepository.findByUserIdAndStatus(userId, CompanyMemberStatus.ACTIVE).stream()
        .filter(
            member ->
                member.getCompany().getStatus() == CompanyStatus.ACTIVE
                    || member.getCompany().getStatus() == CompanyStatus.SUSPENDED)
        .count();
  }

  @Transactional(readOnly = true)
  public Company getCompany(Long companyId) {
    return companyRepository
        .findById(companyId)
        .orElseThrow(() -> new NotFoundException("Company not found"));
  }

  @Transactional
  public Company lockForUpdate(Long companyId) {
    return companyRepository
        .findByIdForUpdate(companyId)
        .orElseThrow(() -> new NotFoundException("Company not found"));
  }

  private void assertWorkspaceCreationAvailable(Long userId) {
    if (maxOwnedWorkspaces < 0) return;
    long owned =
        memberRepository.findByUserIdAndStatus(userId, CompanyMemberStatus.ACTIVE).stream()
            .filter(member -> member.getRole() == CompanyRole.OWNER)
            .filter(
                member ->
                    member.getCompany().getStatus() == CompanyStatus.ACTIVE
                        || member.getCompany().getStatus() == CompanyStatus.SUSPENDED)
            .count();
    if (owned >= maxOwnedWorkspaces) {
      throw new BadRequestException(
          "Workspace creation limit reached. Contact support if you need more workspaces.");
    }
  }

  private CompanyMember requireMembership(Long companyId, Long userId) {
    return memberRepository
        .findByCompanyIdAndUserId(companyId, userId)
        .orElseThrow(() -> new NotFoundException("Company member not found"));
  }

  private void leaveMembership(CompanyMember member) {
    if (member.getStatus() != CompanyMemberStatus.LEFT) {
      member.setStatus(CompanyMemberStatus.LEFT);
      memberRepository.saveAndFlush(member);
    }
    Instant now = Instant.now();
    var assignments =
        roleAssignmentRepository.findActive(
            member.getCompany().getId(), member.getUser().getId(), now);
    assignments.forEach(assignment -> assignment.setRevokedAt(now));
    roleAssignmentRepository.saveAll(assignments);
  }

  private CompanyResponse toResponse(Company company, CompanyRole role) {
    String brandColor =
        preferenceRepository
            .findById(company.getId())
            .map(CompanyPreference::getBrandColor)
            .orElse(DEFAULT_BRAND_COLOR);
    return new CompanyResponse(
        company.getId().toString(),
        company.getName(),
        company.getSlug(),
        company.getBusinessEmail(),
        company.getCountry(),
        company.getTimezone(),
        company.getLogoMediaId() == null ? null : company.getLogoMediaId().toString(),
        company.getStatus(),
        role,
        brandColor);
  }

  private CompanyUserResponse toCompanyUserResponse(CompanyMember member) {
    User user = member.getUser();
    return new CompanyUserResponse(
        member.getId().toString(),
        user.getId().toString(),
        user.getName(),
        user.getUsername(),
        user.getEmail(),
        user.isVerified(),
        user.isTwoFactorEnabled(),
        member.getRole(),
        member.getStatus(),
        member.getCreatedAt(),
        roleAssignmentRepository
            .findActive(member.getCompany().getId(), user.getId(), Instant.now())
            .stream()
            .map(assignment -> assignment.getRole().getName())
            .sorted()
            .toList());
  }

  private Long resolveLogoMediaId(String logoMediaId, long userId) {
    if (logoMediaId == null || logoMediaId.isBlank()) return null;
    long parsedLogoMediaId = parseMediaId(logoMediaId);
    Media media = mediaService.getActiveOwnedMedia(parsedLogoMediaId, userId);
    if (media.getPurpose() != MediaPurpose.COMPANY_LOGO) {
      throw new BadRequestException("Invalid company logo media purpose");
    }
    return parsedLogoMediaId;
  }

  private Long resolveUpdatedLogoMediaId(
      String logoMediaId, Long currentLogoMediaId, long userId) {
    if (logoMediaId == null || logoMediaId.isBlank()) return null;
    long parsedLogoMediaId = parseMediaId(logoMediaId);
    if (Objects.equals(currentLogoMediaId, parsedLogoMediaId)) return currentLogoMediaId;
    return resolveLogoMediaId(logoMediaId, userId);
  }

  private long parseMediaId(String logoMediaId) {
    try {
      return Long.parseLong(logoMediaId);
    } catch (NumberFormatException exception) {
      throw new BadRequestException("Invalid company logo media id");
    }
  }

  private String normalizeOptional(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  private String normalizeBrandColor(String value) {
    String normalized =
        value == null || value.isBlank()
            ? DEFAULT_BRAND_COLOR
            : value.trim().toUpperCase(Locale.ROOT);
    if (!normalized.matches("^#[0-9A-F]{6}$")) {
      throw new BadRequestException("Brand color must be a 6-digit hex color");
    }
    return normalized;
  }

  private String normalizeTimezone(String value) {
    String normalized = normalizeOptional(value);
    if (normalized == null) return null;
    try {
      return ZoneId.of(normalized).getId();
    } catch (DateTimeException exception) {
      throw new BadRequestException("Invalid timezone");
    }
  }

  private String normalizeCountry(String value) {
    String normalized = normalizeOptional(value);
    if (normalized == null) return null;
    Set<String> isoCodes = Arrays.stream(Locale.getISOCountries()).collect(Collectors.toSet());
    String uppercase = normalized.toUpperCase(Locale.ROOT);
    if (isoCodes.contains(uppercase)) return uppercase;

    for (String code : isoCodes) {
      String display = new Locale("", code).getDisplayCountry(Locale.ENGLISH);
      if (display.equalsIgnoreCase(normalized)) return code;
    }
    throw new BadRequestException("Invalid country");
  }

  private String slugify(String value) {
    String slug =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
    if (slug.isBlank()) {
      throw new BadRequestException("Company name must contain letters or numbers");
    }
    return slug.length() <= 100 ? slug : slug.substring(0, 100).replaceAll("-$", "");
  }
}
