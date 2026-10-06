package com.messaging.auth.controller;

import com.messaging.auth.dto.AuthUserResponse;
import com.messaging.auth.dto.CompleteEmailChangeRequest;
import com.messaging.auth.dto.CompletePasswordResetRequest;
import com.messaging.auth.dto.CompleteTwoFactorSettingsRequest;
import com.messaging.auth.dto.EmailChangeChallengeRequest;
import com.messaging.auth.dto.EmailChangeChallengeResponse;
import com.messaging.auth.dto.LoginRequest;
import com.messaging.auth.dto.LoginResponse;
import com.messaging.auth.dto.PasswordResetChallengeResponse;
import com.messaging.auth.dto.PasswordResetRequest;
import com.messaging.auth.dto.RegisterRequest;
import com.messaging.auth.dto.TwoFactorSettingsChallengeRequest;
import com.messaging.auth.dto.TwoFactorSettingsChallengeResponse;
import com.messaging.auth.dto.VerifyEmailRequest;
import com.messaging.auth.dto.VerifyPasswordResetOtpRequest;
import com.messaging.auth.dto.VerifyTwoFactorRequest;
import com.messaging.auth.service.AuthService;
import com.messaging.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @GetMapping("/csrf")
  public ResponseEntity<ApiResponse<String>> csrf(CsrfToken csrfToken) {
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), csrfToken.getToken(), "CSRF token initialized"));
  }

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<AuthUserResponse>> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
    HttpHeaders headers = new HttpHeaders();
    AuthUserResponse response = authService.register(request, servletRequest, headers);
    return ResponseEntity.status(HttpStatus.CREATED)
        .headers(headers)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), response, "Register successful"));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    HttpHeaders headers = new HttpHeaders();
    LoginResponse response = authService.login(request, servletRequest, headers);
    return ResponseEntity.ok()
        .headers(headers)
        .body(ApiResponse.success(HttpStatus.OK.value(), response, "Login successful"));
  }

  @PostMapping("/2fa/verify")
  public ResponseEntity<ApiResponse<AuthUserResponse>> verifyTwoFactor(
      @Valid @RequestBody VerifyTwoFactorRequest request, HttpServletRequest servletRequest) {
    HttpHeaders headers = new HttpHeaders();
    AuthUserResponse response = authService.verifyTwoFactor(request, servletRequest, headers);
    return ResponseEntity.ok()
        .headers(headers)
        .body(ApiResponse.success(HttpStatus.OK.value(), response, "Login successful"));
  }

  @PostMapping("/email-change/challenge")
  public ResponseEntity<ApiResponse<EmailChangeChallengeResponse>> beginEmailChange(
      @Valid @RequestBody EmailChangeChallengeRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            authService.beginEmailChange(request),
            "Verification code sent to the new email"));
  }

  @PostMapping("/email-change/complete")
  public ResponseEntity<ApiResponse<Void>> completeEmailChange(
      @Valid @RequestBody CompleteEmailChangeRequest request) {
    HttpHeaders headers = new HttpHeaders();
    authService.completeEmailChange(request, headers);
    return ResponseEntity.ok()
        .headers(headers)
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(), "Email updated. Please sign in again."));
  }

  @PostMapping("/2fa/settings/challenge")
  public ResponseEntity<ApiResponse<TwoFactorSettingsChallengeResponse>>
      beginTwoFactorSettingsChange(@Valid @RequestBody TwoFactorSettingsChallengeRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            authService.beginTwoFactorSettingsChange(request),
            "Verification code sent"));
  }

  @PostMapping("/2fa/settings/complete")
  public ResponseEntity<ApiResponse<Void>> completeTwoFactorSettingsChange(
      @Valid @RequestBody CompleteTwoFactorSettingsRequest request) {
    HttpHeaders headers = new HttpHeaders();
    authService.completeTwoFactorSettingsChange(request, headers);
    return ResponseEntity.ok()
        .headers(headers)
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                "Two-factor authentication updated. Please sign in again."));
  }

  @PostMapping("/password-reset/request")
  public ResponseEntity<ApiResponse<PasswordResetChallengeResponse>> requestPasswordReset(
      @Valid @RequestBody PasswordResetRequest request) {
    PasswordResetChallengeResponse response = authService.requestPasswordReset(request);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            response,
            "If the account exists, a password reset code has been sent"));
  }

  @PostMapping("/password-reset/verify")
  public ResponseEntity<ApiResponse<Void>> verifyPasswordResetOtp(
      @Valid @RequestBody VerifyPasswordResetOtpRequest request) {
    HttpHeaders headers = new HttpHeaders();
    authService.verifyPasswordResetOtp(request, headers);
    return ResponseEntity.ok()
        .headers(headers)
        .body(ApiResponse.success(HttpStatus.OK.value(), "Password reset code verified"));
  }

  @PostMapping("/password-reset/complete")
  public ResponseEntity<ApiResponse<Void>> completePasswordReset(
      @Valid @RequestBody CompletePasswordResetRequest request, HttpServletRequest servletRequest) {
    HttpHeaders headers = new HttpHeaders();
    authService.completePasswordReset(request, servletRequest, headers);
    return ResponseEntity.ok()
        .headers(headers)
        .body(ApiResponse.success(HttpStatus.OK.value(), "Password reset successful"));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<AuthUserResponse>> refresh(HttpServletRequest servletRequest) {
    HttpHeaders headers = new HttpHeaders();
    AuthUserResponse response = authService.refresh(servletRequest, headers);
    return ResponseEntity.ok()
        .headers(headers)
        .body(ApiResponse.success(HttpStatus.OK.value(), response, "Session refreshed"));
  }

  @PostMapping("/verify-email")
  public ResponseEntity<ApiResponse<AuthUserResponse>> verifyEmail(
      @Valid @RequestBody VerifyEmailRequest request) {
    AuthUserResponse response = authService.verifyEmail(request);
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), response, "Email verified"));
  }

  @PostMapping("/verify-email/resend")
  public ResponseEntity<ApiResponse<Void>> resendEmailVerification() {
    authService.resendEmailVerification();
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), "Verification code sent"));
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest servletRequest) {
    HttpHeaders headers = new HttpHeaders();
    authService.logout(servletRequest, headers);
    return ResponseEntity.ok()
        .headers(headers)
        .body(ApiResponse.success(HttpStatus.OK.value(), "Logout successful"));
  }
}
