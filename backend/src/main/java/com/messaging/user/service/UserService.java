package com.messaging.user.service;

import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.media.dto.ProfilePictureResponse;
import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaPurpose;
import com.messaging.media.exception.MediaException;
import com.messaging.media.service.MediaService;
import com.messaging.security.service.CurrentUserService;
import com.messaging.user.dto.CreateUserRequest;
import com.messaging.user.entity.User;
import com.messaging.user.repository.UserRepository;
import java.util.Locale;
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
  private final PasswordEncoder passwordEncoder;
  private final CurrentUserService currentUserService;
  private final MediaService mediaService;

  @Transactional
  public User createUser(CreateUserRequest request) {
    String email = normalizeEmail(request.email());
    String username = normalizeUsername(request.username());

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
    user.setProfilePicture(media);
    return new ProfilePictureResponse(user.getId().toString(), media.getId().toString());
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
}
