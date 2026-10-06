package com.messaging.user.controller;

import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.common.response.ApiResponse;
import com.messaging.security.service.CurrentUserService;
import com.messaging.user.dto.CreateUserRequest;
import com.messaging.user.dto.ResetPasswordRequest;
import com.messaging.user.dto.UpdateOwnProfileRequest;
import com.messaging.user.dto.UpdateUserRequest;
import com.messaging.user.dto.UserResponse;
import com.messaging.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;
  private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
  private final CurrentUserService currentUserService;

  @PostMapping
  public ResponseEntity<ApiResponse<UserResponse>> createUser(
      @Valid @RequestBody CreateUserRequest request) {
    requireSuperAdmin();
    UserResponse response = userService.toResponse(userService.createUser(request));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), response, "User created"));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<UserResponse>>> getUsers() {
    requireSuperAdmin();
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), userService.getUsers(), "Users fetched"));
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), userService.getCurrentUserResponse(), "User fetched"));
  }

  @PatchMapping("/me/profile")
  public ResponseEntity<ApiResponse<UserResponse>> updateCurrentProfile(
      @Valid @RequestBody UpdateOwnProfileRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), userService.updateOwnProfile(request), "Profile updated"));
  }

  @GetMapping("/{userId}")
  public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable long userId) {
    requireSuperAdmin();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), userService.getUserResponse(userId), "User fetched"));
  }

  @PatchMapping("/{userId}")
  public ResponseEntity<ApiResponse<UserResponse>> updateUser(
      @PathVariable long userId, @Valid @RequestBody UpdateUserRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), userService.updateUser(userId, request), "User updated"));
  }

  @PatchMapping("/{userId}/password")
  public ResponseEntity<ApiResponse<UserResponse>> resetPassword(
      @PathVariable long userId, @Valid @RequestBody ResetPasswordRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), userService.resetPassword(userId, request), "Password reset"));
  }

  @DeleteMapping("/{userId}")
  public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable long userId) {
    userService.deleteUser(userId);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "User deleted"));
  }

  private void requireSuperAdmin() {
    platformAdminAuthorizationService.requireSuperAdmin(currentUserService.currentUserId());
  }
}
