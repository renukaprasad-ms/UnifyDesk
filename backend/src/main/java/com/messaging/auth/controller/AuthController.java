package com.messaging.auth.controller;

import com.messaging.auth.dto.AuthUserResponse;
import com.messaging.auth.dto.LoginRequest;
import com.messaging.auth.dto.RegisterRequest;
import com.messaging.auth.dto.VerifyEmailRequest;
import com.messaging.auth.service.AuthService;
import com.messaging.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

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
  public ResponseEntity<ApiResponse<AuthUserResponse>> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    HttpHeaders headers = new HttpHeaders();
    AuthUserResponse response = authService.login(request, servletRequest, headers);
    return ResponseEntity.ok()
        .headers(headers)
        .body(ApiResponse.success(HttpStatus.OK.value(), response, "Login successful"));
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

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest servletRequest) {
    HttpHeaders headers = new HttpHeaders();
    authService.logout(servletRequest, headers);
    return ResponseEntity.ok()
        .headers(headers)
        .body(ApiResponse.success(HttpStatus.OK.value(), "Logout successful"));
  }
}
