package com.messaging.notification.platform.core.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ForbiddenException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.service.CompanyIamPermissionService;
import com.messaging.notification.platform.core.api.NotificationApiDtos.PreferencesRequest;
import com.messaging.notification.platform.core.api.NotificationApiDtos.QuietHoursRequest;
import com.messaging.notification.platform.core.api.NotificationApiDtos.TypePreferenceRequest;
import com.messaging.notification.platform.core.config.NotificationPlatformProperties;
import com.messaging.notification.platform.core.realtime.NotificationRealtimePublisher;
import com.messaging.notification.platform.core.registry.NotificationTypeRegistry;
import com.messaging.notification.platform.core.state.NotificationUnreadCounterService;
import com.messaging.notification.platform.push.BrowserPushReadiness;
import com.messaging.security.service.CurrentUserService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class NotificationApiServicePolicyTests {

  @Test
  void crossCompanyListRequiresRealActiveMembership() {
    Fixture fixture = fixture();
    when(fixture.jdbc.queryForObject(
            anyString(), eq(Boolean.class), eq(9L), eq(7L)))
        .thenReturn(false);

    assertThatThrownBy(() -> fixture.service.list("9", 20, null, false))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("Company access denied");
  }

  @Test
  void revokedMembershipHidesDirectNotificationLookupAsNotFound() {
    Fixture fixture = fixture();
    when(fixture.jdbc.queryForObject(
            anyString(), eq(Boolean.class), eq(9L), eq(7L)))
        .thenReturn(false);

    assertThatThrownBy(() -> fixture.service.get("55", "9"))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Notification not found");
  }

  @Test
  void unreadCountUsesOneCurrentPermissionSnapshot() {
    Fixture fixture = fixture();
    when(fixture.jdbc.queryForObject(
            anyString(), eq(Boolean.class), eq(9L), eq(7L)))
        .thenReturn(true);
    when(fixture.permissions.effectivePermissions(7L, 9L))
        .thenReturn(Set.of("email.read"));
    when(fixture.counters.counts(7L, 9L, Set.of("email.read")))
        .thenReturn(new NotificationUnreadCounterService.Counts(2, 4, 6));

    NotificationApiDtos.UnreadCount result = fixture.service.unreadCount("9");

    assertThat(result).isEqualTo(new NotificationApiDtos.UnreadCount(2, 4, 6));
    verify(fixture.permissions, times(1)).effectivePermissions(7L, 9L);
  }

  @Test
  void mandatorySecurityEmailCannotBeDisabled() {
    Fixture fixture = fixture();

    PreferencesRequest request =
        new PreferencesRequest(
            "UTC",
            new QuietHoursRequest(true, "22:00", "08:00"),
            List.of(new TypePreferenceRequest("SECURITY_ACCOUNT_CRITICAL", "OFF", false)));

    assertThatThrownBy(() -> fixture.service.updatePreferences(null, request))
        .isInstanceOf(BadRequestException.class)
        .hasMessage("Mandatory notification email cannot be disabled");
  }

  @Test
  void preferencesUseSharedEncryptionAwarePushReadiness() {
    Fixture fixture = fixture();
    when(fixture.pushReadiness.ready()).thenReturn(false);

    NotificationApiDtos.PreferencesResponse response = fixture.service.preferences(null);

    assertThat(response.channels().browserPushConfigured()).isFalse();
  }

  @Test
  void invalidTimezoneIsRejectedBeforePreferenceReplacement() {
    Fixture fixture = fixture();

    PreferencesRequest request =
        new PreferencesRequest(
            "Mars/Olympus",
            new QuietHoursRequest(true, "22:00", "08:00"),
            List.of());

    assertThatThrownBy(() -> fixture.service.updatePreferences(null, request))
        .isInstanceOf(BadRequestException.class)
        .hasMessage("Invalid notification timezone");
  }

  private Fixture fixture() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    CurrentUserService currentUser = org.mockito.Mockito.mock(CurrentUserService.class);
    when(currentUser.currentUserId()).thenReturn(7L);
    CompanyIamPermissionService permissions =
        org.mockito.Mockito.mock(CompanyIamPermissionService.class);
    NotificationUnreadCounterService counters =
        org.mockito.Mockito.mock(NotificationUnreadCounterService.class);
    NotificationRealtimePublisher realtime =
        org.mockito.Mockito.mock(NotificationRealtimePublisher.class);
    NotificationTypeRegistry registry = new NotificationTypeRegistry();
    BrowserPushReadiness pushReadiness =
        org.mockito.Mockito.mock(BrowserPushReadiness.class);
    when(pushReadiness.ready()).thenReturn(true);
    NotificationApiService service =
        new NotificationApiService(
            jdbc,
            currentUser,
            permissions,
            counters,
            realtime,
            registry,
            new NotificationCursorCodec(new ObjectMapper()),
            new NotificationPlatformProperties(),
            pushReadiness);
    return new Fixture(jdbc, permissions, counters, pushReadiness, service);
  }

  private record Fixture(
      JdbcTemplate jdbc,
      CompanyIamPermissionService permissions,
      NotificationUnreadCounterService counters,
      BrowserPushReadiness pushReadiness,
      NotificationApiService service) {}
}
