package com.messaging.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.auth.service.PasswordPolicyService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ForbiddenException;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.company.repository.CompanyMemberRoleAssignmentRepository;
import com.messaging.lifecycle.config.DeletionLifecycleProperties;
import com.messaging.lifecycle.service.DeletionPurgeService;
import com.messaging.media.service.MediaService;
import com.messaging.realtime.service.RealtimeConnectionRegistry;
import com.messaging.security.service.CurrentUserService;
import com.messaging.session.service.UserSessionService;
import com.messaging.user.dto.UpdateUserRequest;
import com.messaging.user.entity.User;
import com.messaging.user.enums.UserStatus;
import com.messaging.user.repository.UserPreferenceRepository;
import com.messaging.user.repository.UserRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceSecurityTests {

  private static final long USER_ID = 42L;

  @Mock private UserRepository userRepository;
  @Mock private UserPreferenceRepository userPreferenceRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private CurrentUserService currentUserService;
  @Mock private MediaService mediaService;
  @Mock private UserSessionService userSessionService;
  @Mock private CompanyMemberRepository companyMemberRepository;
  @Mock private CompanyMemberRoleAssignmentRepository roleAssignmentRepository;
  @Mock private DeletionPurgeService deletionPurgeService;
  @Mock private DeletionLifecycleProperties deletionProperties;
  @Mock private RealtimeConnectionRegistry realtimeConnectionRegistry;
  @Mock private PasswordPolicyService passwordPolicyService;

  @InjectMocks private UserService service;

  private User user;

  @BeforeEach
  void setUp() {
    user = new User();
    user.setId(USER_ID);
    user.setEmail("old@example.com");
    user.setName("User");
    user.setUsername("user");
    user.setPassword("encoded-password");
    user.setVerified(true);
    user.setStatus(UserStatus.ACTIVE);
  }

  @Test
  void genericProfileMutationCannotChangeEmail() {
    when(currentUserService.currentUserId()).thenReturn(USER_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    UpdateUserRequest request =
        new UpdateUserRequest("new@example.com", null, null, null);

    assertThatThrownBy(() -> service.updateUser(USER_ID, request))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("secure email change");
  }

  @Test
  void accountDeletionRequiresCurrentPassword() {
    when(currentUserService.currentUserId()).thenReturn(USER_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

    assertThatThrownBy(() -> service.deleteUser(USER_ID, "wrong-password"))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("Current password");

    verify(userSessionService, never()).revokeAllForUser(USER_ID);
    verify(realtimeConnectionRegistry, never()).closeForUser(USER_ID);
  }

  @Test
  void accountDeletionImmediatelyTerminatesMembershipAndAuthentication() {
    when(currentUserService.currentUserId()).thenReturn(USER_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("correct-password", "encoded-password")).thenReturn(true);
    when(companyMemberRepository.findByUserIdAndStatus(USER_ID, CompanyMemberStatus.ACTIVE))
        .thenReturn(List.of());
    when(companyMemberRepository.findByUserIdAndStatus(USER_ID, CompanyMemberStatus.SUSPENDED))
        .thenReturn(List.of());
    when(roleAssignmentRepository.findByUserIdAndRevokedAtIsNull(USER_ID)).thenReturn(List.of());
    when(deletionProperties.getRetention()).thenReturn(Duration.ofDays(30));

    service.deleteUser(USER_ID, "correct-password");

    assertThat(user.getStatus()).isEqualTo(UserStatus.DELETION_PENDING);
    verify(userSessionService).revokeAllForUser(USER_ID);
    verify(realtimeConnectionRegistry).closeForUser(USER_ID);
    verify(userRepository).saveAndFlush(user);
  }
}
