package com.messaging.company.service;

import com.messaging.billing.service.EntitlementService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.ForbiddenException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.config.CompanyInvitationProperties;
import com.messaging.company.dto.AcceptCompanyInvitationResponse;
import com.messaging.company.dto.CompanyInvitationResponse;
import com.messaging.company.dto.CompanyInvitationResponse.InvitationRoleResponse;
import com.messaging.company.dto.CreateCompanyInvitationRequest;
import com.messaging.company.entity.Company;
import com.messaging.company.entity.CompanyCustomRole;
import com.messaging.company.entity.CompanyInvitation;
import com.messaging.company.entity.CompanyMember;
import com.messaging.company.entity.CompanyMemberRoleAssignment;
import com.messaging.company.enums.CompanyInvitationStatus;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.enums.CompanyRole;
import com.messaging.company.enums.CompanyStatus;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.repository.CompanyCustomRolePermissionRepository;
import com.messaging.company.repository.CompanyCustomRoleRepository;
import com.messaging.company.repository.CompanyInvitationRepository;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.company.repository.CompanyMemberRoleAssignmentRepository;
import com.messaging.notification.dto.SendNotificationRequest;
import com.messaging.notification.platform.core.event.NotificationEventRecorder;
import com.messaging.notification.service.NotificationService;
import com.messaging.security.service.CurrentUserService;
import com.messaging.user.entity.User;
import com.messaging.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyInvitationService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final CompanyInvitationRepository invitationRepository;
  private final CompanyMemberRepository memberRepository;
  private final CompanyCustomRoleRepository roleRepository;
  private final CompanyCustomRolePermissionRepository rolePermissionRepository;
  private final CompanyMemberRoleAssignmentRepository assignmentRepository;
  private final CompanyAuthorizationService authorizationService;
  private final CompanyIamPermissionService iamPermissionService;
  private final CompanyService companyService;
  private final EntitlementService entitlementService;
  private final CurrentUserService currentUserService;
  private final UserRepository userRepository;
  private final NotificationService notificationService;
  private final NotificationEventRecorder notificationEventRecorder;
  private final CompanyInvitationProperties properties;

  @Transactional
  public CompanyInvitationResponse invite(Long companyId, CreateCompanyInvitationRequest request) {
    Long actorId = currentUserService.currentUserId();
    authorizationService.requirePermission(actorId, companyId, CompanyPermissionKeys.USER_INVITE);

    Company company = companyService.lockForUpdate(companyId);
    requireActiveCompany(company);
    String email = normalizeEmail(request.email());
    rejectExistingMember(companyId, email);

    CompanyInvitation existingPending =
        invitationRepository
            .findFirstByCompanyIdAndEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                companyId, email, CompanyInvitationStatus.PENDING)
            .orElse(null);

    Instant now = Instant.now();
    if (existingPending != null && existingPending.getExpiresAt().isAfter(now)) {
      throw new ConflictException("An active invitation already exists for this email");
    }

    Set<CompanyCustomRole> roles = validateRoles(actorId, companyId, request.roleIds());
    assertSeatAvailableForInvite(companyId, now);

    CompanyInvitation invitation =
        existingPending == null ? new CompanyInvitation() : existingPending;
    String rawToken = newToken();
    invitation.setCompany(company);
    invitation.setEmail(email);
    invitation.setStatus(CompanyInvitationStatus.PENDING);
    invitation.setTokenHash(hashToken(rawToken));
    invitation.setInvitedByUserId(actorId);
    invitation.setExpiresAt(now.plus(properties.getExpiry()));
    invitation.setAcceptedAt(null);
    invitation.setAcceptedByUserId(null);
    invitation.setRoles(new LinkedHashSet<>(roles));
    invitation = invitationRepository.saveAndFlush(invitation);

    recordInvitationForExistingUser(invitation, actorId);
    sendInvitationEmail(invitation, rawToken);
    return toResponse(invitation, now);
  }

  @Transactional(readOnly = true)
  public List<CompanyInvitationResponse> invitations(Long companyId) {
    Long actorId = currentUserService.currentUserId();
    authorizationService.requirePermission(actorId, companyId, CompanyPermissionKeys.USER_READ);
    Instant now = Instant.now();
    return invitationRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
        .map(invitation -> toResponse(invitation, now))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<CompanyInvitationResponse> myInvitations() {
    User user = currentUser();
    Instant now = Instant.now();
    return invitationRepository.findByEmailIgnoreCaseOrderByCreatedAtDesc(user.getEmail()).stream()
        .map(invitation -> toResponse(invitation, now))
        .toList();
  }

  @Transactional
  public AcceptCompanyInvitationResponse acceptCurrentUserInvitation(Long invitationId) {
    User user = currentUser();
    CompanyInvitation invitation =
        invitationRepository
            .findByIdAndEmailIgnoreCase(invitationId, user.getEmail())
            .orElseThrow(() -> new NotFoundException("Invitation not found"));
    return acceptInvitation(invitation, user);
  }

  @Transactional
  public CompanyInvitationResponse resend(Long companyId, Long invitationId) {
    Long actorId = currentUserService.currentUserId();
    authorizationService.requirePermission(actorId, companyId, CompanyPermissionKeys.USER_INVITE);
    companyService.lockForUpdate(companyId);
    CompanyInvitation invitation = requireInvitation(companyId, invitationId);

    if (invitation.getStatus() == CompanyInvitationStatus.ACCEPTED) {
      throw new ConflictException("Accepted invitations cannot be resent");
    }
    if (invitation.getStatus() == CompanyInvitationStatus.REVOKED) {
      throw new ConflictException("Revoked invitations cannot be resent");
    }

    Instant now = Instant.now();
    if (!invitation.getExpiresAt().isAfter(now)) {
      assertSeatAvailableForInvite(companyId, now);
    }
    validateExistingRoles(actorId, companyId, invitation.getRoles());

    String rawToken = newToken();
    invitation.setTokenHash(hashToken(rawToken));
    invitation.setStatus(CompanyInvitationStatus.PENDING);
    invitation.setInvitedByUserId(actorId);
    invitation.setExpiresAt(now.plus(properties.getExpiry()));
    invitation = invitationRepository.saveAndFlush(invitation);

    recordInvitationForExistingUser(invitation, actorId);
    sendInvitationEmail(invitation, rawToken);
    return toResponse(invitation, now);
  }

  @Transactional
  public void revoke(Long companyId, Long invitationId) {
    Long actorId = currentUserService.currentUserId();
    authorizationService.requirePermission(actorId, companyId, CompanyPermissionKeys.USER_INVITE);
    CompanyInvitation invitation = requireInvitation(companyId, invitationId);
    if (invitation.getStatus() == CompanyInvitationStatus.ACCEPTED) {
      throw new ConflictException("Accepted invitations cannot be revoked");
    }
    if (invitation.getStatus() != CompanyInvitationStatus.REVOKED) {
      invitation.setStatus(CompanyInvitationStatus.REVOKED);
      invitationRepository.saveAndFlush(invitation);
    }
  }

  @Transactional
  public AcceptCompanyInvitationResponse accept(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new NotFoundException("Invitation not found");
    }

    CompanyInvitation invitation =
        invitationRepository
            .findByTokenHash(hashToken(rawToken))
            .orElseThrow(() -> new NotFoundException("Invitation not found"));

    return acceptInvitation(invitation, currentUser());
  }

  private AcceptCompanyInvitationResponse acceptInvitation(
      CompanyInvitation invitation, User user) {
    if (invitation.getStatus() != CompanyInvitationStatus.PENDING) {
      throw new ConflictException("Invitation is no longer active");
    }

    Instant now = Instant.now();
    if (!invitation.getExpiresAt().isAfter(now)) {
      throw new BadRequestException("Invitation has expired");
    }

    Company company = companyService.lockForUpdate(invitation.getCompany().getId());
    requireActiveCompany(company);

    Long userId = user.getId();
    if (!user.isVerified()) {
      throw new ForbiddenException("Verify your email before accepting the invitation");
    }
    if (!normalizeEmail(user.getEmail()).equals(invitation.getEmail())) {
      throw new ForbiddenException("This invitation belongs to a different email address");
    }

    CompanyMember member =
        memberRepository.findByCompanyIdAndUserId(company.getId(), userId).orElse(null);

    if (member == null) {
      assertSeatAvailableForAcceptance(company.getId());
      member = new CompanyMember();
      member.setCompany(company);
      member.setUser(user);
      member.setRole(CompanyRole.MEMBER);
      member.setStatus(CompanyMemberStatus.ACTIVE);
      member = memberRepository.saveAndFlush(member);
    } else if (member.getStatus() == CompanyMemberStatus.SUSPENDED) {
      throw new ForbiddenException("Your company membership is suspended");
    } else if (member.getStatus() != CompanyMemberStatus.ACTIVE) {
      assertSeatAvailableForAcceptance(company.getId());
      member.setRole(CompanyRole.MEMBER);
      member.setStatus(CompanyMemberStatus.ACTIVE);
      member = memberRepository.saveAndFlush(member);
    }

    assignInvitationRoles(invitation, userId);

    invitation.setStatus(CompanyInvitationStatus.ACCEPTED);
    invitation.setAcceptedAt(now);
    invitation.setAcceptedByUserId(userId);
    invitationRepository.saveAndFlush(invitation);

    if (!invitation.getInvitedByUserId().equals(userId)) {
      notificationEventRecorder.recordCompanyInvitationAccepted(
          invitation.getId(),
          company.getId(),
          userId,
          invitation.getInvitedByUserId(),
          company.getName(),
          user.getName());
    }

    return new AcceptCompanyInvitationResponse(
        company.getId().toString(), company.getName(), member.getId().toString());
  }

  private void rejectExistingMember(Long companyId, String email) {
    userRepository
        .findByEmailIgnoreCase(email)
        .flatMap(user -> memberRepository.findByCompanyIdAndUserId(companyId, user.getId()))
        .ifPresent(
            member -> {
              if (member.getStatus() == CompanyMemberStatus.ACTIVE) {
                throw new ConflictException("This user is already a company member");
              }
              if (member.getStatus() == CompanyMemberStatus.SUSPENDED) {
                throw new ConflictException("This user already has a suspended company membership");
              }
            });
  }

  private Set<CompanyCustomRole> validateRoles(
      Long actorId, Long companyId, Set<String> requestedRoleIds) {
    if (requestedRoleIds == null || requestedRoleIds.isEmpty()) {
      return Set.of();
    }
    authorizationService.requirePermission(actorId, companyId, CompanyPermissionKeys.ROLE_READ);
    authorizationService.requirePermission(actorId, companyId, CompanyPermissionKeys.ROLE_ASSIGN);

    LinkedHashSet<CompanyCustomRole> roles = new LinkedHashSet<>();
    for (String rawRoleId : requestedRoleIds) {
      Long roleId = parseId(rawRoleId);
      CompanyCustomRole role =
          roleRepository
              .findByIdAndCompanyIdAndDeletedAtIsNull(roleId, companyId)
              .orElseThrow(() -> new NotFoundException("Company role not found"));
      if (role.isSystemRole()) {
        throw new BadRequestException(
            "System roles are managed by company membership and cannot be assigned manually");
      }
      roles.add(role);
    }
    validateExistingRoles(actorId, companyId, roles);
    return Set.copyOf(roles);
  }

  private void validateExistingRoles(Long actorId, Long companyId, Set<CompanyCustomRole> roles) {
    if (roles.isEmpty()) return;
    if (roles.stream().anyMatch(CompanyCustomRole::isSystemRole)) {
      throw new BadRequestException(
          "System roles are managed by company membership and cannot be assigned manually");
    }
    authorizationService.requirePermission(actorId, companyId, CompanyPermissionKeys.ROLE_READ);
    authorizationService.requirePermission(actorId, companyId, CompanyPermissionKeys.ROLE_ASSIGN);

    LinkedHashSet<String> permissions = new LinkedHashSet<>();
    roles.forEach(
        role ->
            rolePermissionRepository.findByRoleId(role.getId()).stream()
                .map(permission -> permission.getPermissionKey())
                .forEach(permissions::add));

    if (!iamPermissionService.canDelegate(actorId, companyId, permissions)) {
      throw new ForbiddenException(
          "You cannot invite users with permissions that you do not possess");
    }
  }

  private void assertSeatAvailableForInvite(Long companyId, Instant now) {
    long activeMembers =
        memberRepository.countByCompanyIdAndStatus(companyId, CompanyMemberStatus.ACTIVE);
    long pendingInvitations =
        invitationRepository.countByCompanyIdAndStatusAndExpiresAtAfter(
            companyId, CompanyInvitationStatus.PENDING, now);
    entitlementService.assertWithinLimit(
        companyId, "MAX_WORKSPACE_USERS", activeMembers + pendingInvitations);
  }

  private void assertSeatAvailableForAcceptance(Long companyId) {
    long activeMembers =
        memberRepository.countByCompanyIdAndStatus(companyId, CompanyMemberStatus.ACTIVE);
    entitlementService.assertWithinLimit(companyId, "MAX_WORKSPACE_USERS", activeMembers);
  }

  private void assignInvitationRoles(CompanyInvitation invitation, Long userId) {
    for (CompanyCustomRole role : invitation.getRoles()) {
      if (role.getDeletedAt() != null) {
        continue;
      }
      CompanyMemberRoleAssignment assignment =
          assignmentRepository
              .findByCompanyIdAndUserIdAndRoleId(
                  invitation.getCompany().getId(), userId, role.getId())
              .orElseGet(CompanyMemberRoleAssignment::new);
      assignment.setCompanyId(invitation.getCompany().getId());
      assignment.setUserId(userId);
      assignment.setRole(role);
      assignment.setAssignedByUserId(invitation.getInvitedByUserId());
      assignment.setExpiresAt(null);
      assignment.setRevokedAt(null);
      assignmentRepository.save(assignment);
    }
    assignmentRepository.flush();
  }

  private CompanyInvitation requireInvitation(Long companyId, Long invitationId) {
    return invitationRepository
        .findByIdAndCompanyId(invitationId, companyId)
        .orElseThrow(() -> new NotFoundException("Invitation not found"));
  }

  private void requireActiveCompany(Company company) {
    if (company.getStatus() != CompanyStatus.ACTIVE) {
      throw new BadRequestException("Company is not active");
    }
  }

  private User currentUser() {
    Long userId = currentUserService.currentUserId();
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new NotFoundException("User not found"));
  }

  private void recordInvitationForExistingUser(
      CompanyInvitation invitation, Long actorUserId) {
    userRepository
        .findByEmailIgnoreCase(invitation.getEmail())
        .filter(User::isVerified)
        .filter(user -> !user.getId().equals(actorUserId))
        .ifPresent(
            user ->
                notificationEventRecorder.recordCompanyInvitation(
                    invitation.getId(),
                    invitation.getCompany().getId(),
                    actorUserId,
                    user.getId(),
                    invitation.getCompany().getName()));
  }

  private void sendInvitationEmail(CompanyInvitation invitation, String rawToken) {
    String baseUrl = properties.getFrontendBaseUrl().replaceAll("/+$", "");
    String acceptUrl = baseUrl + "/invite/" + rawToken;
    String body =
        "You have been invited to join "
            + invitation.getCompany().getName()
            + " on UnifyDesk.\n\n"
            + "Accept invitation:\n"
            + acceptUrl
            + "\n\n"
            + "This invitation expires in "
            + properties.getExpiry().toHours()
            + " hours. If you did not expect this invitation, you can ignore this email.";

    notificationService.send(
        SendNotificationRequest.email(
            invitation.getEmail(),
            "You're invited to " + invitation.getCompany().getName(),
            body,
            false));
  }

  private CompanyInvitationResponse toResponse(CompanyInvitation invitation, Instant now) {
    CompanyInvitationStatus status =
        invitation.getStatus() == CompanyInvitationStatus.PENDING
                && !invitation.getExpiresAt().isAfter(now)
            ? CompanyInvitationStatus.EXPIRED
            : invitation.getStatus();

    List<InvitationRoleResponse> roles =
        invitation.getRoles().stream()
            .sorted(Comparator.comparing(CompanyCustomRole::getName))
            .map(role -> new InvitationRoleResponse(role.getId().toString(), role.getName()))
            .toList();

    return new CompanyInvitationResponse(
        invitation.getId().toString(),
        invitation.getCompany().getId().toString(),
        invitation.getCompany().getName(),
        invitation.getEmail(),
        status,
        roles,
        invitation.getExpiresAt(),
        invitation.getCreatedAt());
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private Long parseId(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw new BadRequestException("Invalid role id");
    }
  }

  private String newToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
