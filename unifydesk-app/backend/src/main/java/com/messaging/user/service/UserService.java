package com.messaging.user.service;

import com.messaging.auth.service.PasswordPolicyService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.ForbiddenException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.enums.CompanyRole;
import com.messaging.company.enums.CompanyStatus;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.company.repository.CompanyMemberRoleAssignmentRepository;
import com.messaging.lifecycle.config.DeletionLifecycleProperties;
import com.messaging.lifecycle.enums.LifecycleEntityType;
import com.messaging.lifecycle.service.DeletionPurgeService;
import com.messaging.media.dto.ProfilePictureResponse;
import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaPurpose;
import com.messaging.media.exception.MediaException;
import com.messaging.media.service.MediaService;
import com.messaging.realtime.service.RealtimeConnectionRegistry;
import com.messaging.security.service.CurrentUserService;
import com.messaging.session.service.UserSessionService;
import com.messaging.user.dto.CreateUserRequest;
import com.messaging.user.dto.ResetPasswordRequest;
import com.messaging.user.dto.UpdateOwnProfileRequest;
import com.messaging.user.dto.UpdateUserPreferencesRequest;
import com.messaging.user.dto.UpdateUserRequest;
import com.messaging.user.dto.UserPreferencesResponse;
import com.messaging.user.dto.UserResponse;
import com.messaging.user.entity.User;
import com.messaging.user.entity.UserPreference;
import com.messaging.user.enums.UserAppearanceMode;
import com.messaging.user.enums.UserStatus;
import com.messaging.user.repository.UserPreferenceRepository;
import com.messaging.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final UserPreferenceRepository userPreferenceRepository;
  private final PasswordEncoder passwordEncoder;
  private final CurrentUserService currentUserService;
  private final MediaService mediaService;
  private final UserSessionService userSessionService;
  private final CompanyMemberRepository companyMemberRepository;
  private final CompanyMemberRoleAssignmentRepository roleAssignmentRepository;
  private final DeletionPurgeService deletionPurgeService;
  private final DeletionLifecycleProperties deletionProperties;
  private final RealtimeConnectionRegistry realtimeConnectionRegistry;
  private final PasswordPolicyService passwordPolicyService;

  @Transactional
  public User createUser(CreateUserRequest request) {
    String email = normalizeEmail(request.email());
    String username = normalizeUsername(request.username());
    passwordPolicyService.validate(request.password());

    userRepository
        .findByEmailOrUsername(email, username)
        .ifPresent(
            existingUser -> {
              if (existingUser.getEmail().equals(email)) {
                throw new ConflictException("Email already exists");
              }
              throw new ConflictException("Username already exists");
            });

    User user = new User();
    user.setEmail(email);
    user.setName(request.name().trim());
    user.setVerified(false);
    user.setUsername(username);
    user.setPassword(passwordEncoder.encode(request.password()));
    user.setTwoFactorEnabled(request.twoFactorEnabled());
    user.setStatus(UserStatus.ACTIVE);

    try {
      return userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException("Email or username already exists");
    }
  }

  @Transactional(readOnly = true)
  public User getUser(long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new NotFoundException("User not found"));
  }

  @Transactional(readOnly = true)
  public List<UserResponse> getUsers() {
    return userRepository.findAll().stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public UserResponse getUserResponse(long userId) {
    return toResponse(getUser(userId));
  }

  @Transactional(readOnly = true)
  public UserResponse getCurrentUserResponse() {
    return toResponse(getUser(currentUserService.currentUserId()));
  }

  @Transactional(readOnly = true)
  public UserPreferencesResponse getCurrentUserPreferences() {
    return new UserPreferencesResponse(appearanceMode(currentUserService.currentUserId()));
  }

  @Transactional
  public UserPreferencesResponse updateCurrentUserPreferences(
      UpdateUserPreferencesRequest request) {
    long userId = currentUserService.currentUserId();
    getUser(userId);
    UserPreference preference =
        userPreferenceRepository.findById(userId).orElseGet(UserPreference::new);
    preference.setUserId(userId);
    preference.setAppearanceMode(request.appearanceMode());
    userPreferenceRepository.saveAndFlush(preference);
    return new UserPreferencesResponse(preference.getAppearanceMode());
  }

  @Transactional(readOnly = true)
  public UserAppearanceMode appearanceMode(long userId) {
    return userPreferenceRepository
        .findById(userId)
        .map(UserPreference::getAppearanceMode)
        .orElse(UserAppearanceMode.SYSTEM);
  }

  @Transactional(readOnly = true)
  public String validateEmailChangeCandidate(long userId, String newEmail) {
    User user = getUser(userId);
    String normalized = normalizeEmail(newEmail);
    if (normalized.equals(user.getEmail())) {
      throw new BadRequestException("New email must be different from your current email");
    }
    if (userRepository.existsByEmailAndIdNot(normalized, userId)) {
      throw new ConflictException("Email already exists");
    }
    return normalized;
  }

  @Transactional
  public User completeEmailChange(long userId, String newEmail) {
    User user =
        userRepository
            .findByIdForUpdate(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));
    String normalized = normalizeEmail(newEmail);
    if (userRepository.existsByEmailAndIdNot(normalized, userId)) {
      throw new ConflictException("Email already exists");
    }
    user.setEmail(normalized);
    user.setVerified(true);
    try {
      return userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException("Email already exists");
    }
  }

  @Transactional
  public UserResponse updateOwnProfile(UpdateOwnProfileRequest request) {
    long userId = currentUserService.currentUserId();
    User user = getUser(userId);
    String username = normalizeUsername(request.username());

    if (userRepository.existsByUsernameAndIdNot(username, userId)) {
      throw new ConflictException("Username already exists");
    }

    user.setName(request.name().trim());
    user.setUsername(username);
    return toResponse(user);
  }

  @Transactional
  public UserResponse updateUser(long userId, UpdateUserRequest request) {
    requireCurrentUser(userId, "You can only update your own user profile");
    User user = getUser(userId);

    if (request.email() != null
        && !request.email().isBlank()
        && !normalizeEmail(request.email()).equals(user.getEmail())) {
      throw new BadRequestException("Use the secure email change flow to update your email");
    }

    if (request.username() != null && !request.username().isBlank()) {
      String username = normalizeUsername(request.username());
      if (userRepository.existsByUsernameAndIdNot(username, userId)) {
        throw new ConflictException("Username already exists");
      }
      user.setUsername(username);
    }

    if (request.name() != null && !request.name().isBlank()) {
      user.setName(request.name().trim());
    }

    if (request.verified() != null) {
      throw new BadRequestException("Email verification status cannot be changed directly");
    }

    return toResponse(user);
  }

  @Transactional
  public UserResponse resetPassword(long userId, ResetPasswordRequest request) {
    requireCurrentUser(userId, "You can only change your own password");

    User user = getUser(userId);
    if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
      throw new ForbiddenException("Current password is incorrect");
    }
    if (passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new ConflictException("New password must be different from current password");
    }
    passwordPolicyService.validate(request.password());

    user.setPassword(passwordEncoder.encode(request.password()));
    userSessionService.revokeAllForUser(userId);
    realtimeConnectionRegistry.closeForUser(userId);
    return toResponse(user);
  }

  @Transactional
  public UserResponse resetForgottenPassword(long userId, String password) {
    User user = getUser(userId);
    if (passwordEncoder.matches(password, user.getPassword())) {
      throw new ConflictException("New password must be different from current password");
    }
    passwordPolicyService.validate(password);
    user.setPassword(passwordEncoder.encode(password));
    return toResponse(user);
  }

  @Transactional
  public UserResponse setTwoFactorEnabled(long userId, boolean enabled) {
    User user = getUser(userId);
    user.setTwoFactorEnabled(enabled);
    return toResponse(user);
  }

  @Transactional
  public void deleteUser(long userId, String currentPassword) {
    requireCurrentUser(userId, "You can only delete your own account");

    User user = getUser(userId);
    if (user.getStatus() == UserStatus.DELETION_PENDING
        || user.getStatus() == UserStatus.PURGING
        || user.getStatus() == UserStatus.PURGED) {
      return;
    }
    if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
      throw new ForbiddenException("Current password is incorrect");
    }

    var activeMemberships =
        companyMemberRepository.findByUserIdAndStatus(userId, CompanyMemberStatus.ACTIVE);
    var suspendedMemberships =
        companyMemberRepository.findByUserIdAndStatus(userId, CompanyMemberStatus.SUSPENDED);
    boolean ownsCompany =
        java.util.stream.Stream.concat(activeMemberships.stream(), suspendedMemberships.stream())
            .anyMatch(
                member ->
                    member.getRole() == CompanyRole.OWNER
                        && (member.getCompany().getStatus() == CompanyStatus.ACTIVE
                            || member.getCompany().getStatus() == CompanyStatus.SUSPENDED));
    if (ownsCompany) {
      throw new BadRequestException(
          "Transfer ownership or delete owned companies before deleting your account");
    }

    Instant now = Instant.now();
    Instant purgeAfter = now.plus(deletionProperties.getRetention());

    java.util.stream.Stream.concat(activeMemberships.stream(), suspendedMemberships.stream())
        .forEach(member -> member.setStatus(CompanyMemberStatus.LEFT));
    companyMemberRepository.saveAll(activeMemberships);
    companyMemberRepository.saveAll(suspendedMemberships);

    var assignments = roleAssignmentRepository.findByUserIdAndRevokedAtIsNull(userId);
    assignments.forEach(assignment -> assignment.setRevokedAt(now));
    roleAssignmentRepository.saveAll(assignments);

    // Authentication and realtime access terminate immediately; relational rows remain for
    // retention/audit purposes until the purge lifecycle completes.
    userSessionService.revokeAllForUser(userId);
    realtimeConnectionRegistry.closeForUser(userId);

    user.setStatus(UserStatus.DELETION_PENDING);
    user.setDeletedAt(now);
    user.setPurgeAfter(purgeAfter);
    user.setDeletionRequestedBy(userId);
    user.setPurgeStartedAt(null);
    user.setPurgedAt(null);
    userRepository.saveAndFlush(user);

    deletionPurgeService.schedule(LifecycleEntityType.USER, userId, null, userId, purgeAfter);
  }

  @Transactional
  public User verifyEmail(long userId) {
    User user = getUser(userId);
    user.setVerified(true);
    return user;
  }

  @Transactional
  public ProfilePictureResponse attachCurrentUserProfilePicture(String mediaId) {
    long userId = currentUserService.currentUserId();
    long parsedMediaId = parseMediaId(mediaId);
    User user =
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    Media media = mediaService.getActiveOwnedMedia(parsedMediaId, userId);
    if (media.getPurpose() != MediaPurpose.USER_PROFILE) {
      throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID_PURPOSE");
    }

    Media previous = user.getProfilePicture();
    user.setProfilePicture(media);
    userRepository.saveAndFlush(user);
    if (previous != null && !previous.getId().equals(media.getId())) {
      mediaService.retirePersonalProfilePicture(previous.getId(), userId);
    }
    return new ProfilePictureResponse(user.getId().toString(), media.getId().toString());
  }

  @Transactional
  public void removeCurrentUserProfilePicture() {
    long userId = currentUserService.currentUserId();
    User user = getUser(userId);
    Media previous = user.getProfilePicture();
    if (previous == null) {
      return;
    }
    user.setProfilePicture(null);
    userRepository.saveAndFlush(user);
    mediaService.retirePersonalProfilePicture(previous.getId(), userId);
  }

  @Transactional
  public ProfilePictureResponse attachUserProfilePicture(long userId, Media media) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    if (media.getPurpose() != MediaPurpose.USER_PROFILE) {
      throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID_PURPOSE");
    }
    user.setProfilePicture(media);
    return new ProfilePictureResponse(user.getId().toString(), media.getId().toString());
  }

  @Transactional(readOnly = true)
  public Optional<User> findByIdentifier(String identifier) {
    if (identifier == null || identifier.isBlank()) {
      return Optional.empty();
    }
    String normalized = identifier.trim().toLowerCase(Locale.ROOT);
    return userRepository.findByEmailOrUsername(normalized, normalized);
  }

  private void requireCurrentUser(long userId, String message) {
    if (currentUserService.currentUserId() != userId) {
      throw new ForbiddenException(message);
    }
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  @Transactional(readOnly = true)
  public User getByEmail(String email) {
    return userRepository
        .findByEmail(normalizeEmail(email))
        .orElseThrow(() -> new NotFoundException("User not found"));
  }

  private String normalizeUsername(String username) {
    return username.trim().toLowerCase(Locale.ROOT);
  }

  private long parseMediaId(String mediaId) {
    try {
      return Long.parseLong(mediaId);
    } catch (NumberFormatException exception) {
      throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID_ID");
    }
  }

  public UserResponse toResponse(User user) {
    return new UserResponse(
        user.getId().toString(),
        user.getEmail(),
        user.getName(),
        user.isVerified(),
        user.getUsername(),
        user.isTwoFactorEnabled(),
        user.getProfilePicture() == null ? null : user.getProfilePicture().getId().toString());
  }
}
