package com.messaging.media.controller;

import com.messaging.common.response.ApiResponse;
import com.messaging.media.dto.ProfilePictureResponse;
import com.messaging.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserMediaController {

  private final UserService userService;

  @PutMapping("/profile-picture/{mediaId}")
  public ResponseEntity<ApiResponse<ProfilePictureResponse>> attachProfilePicture(
      @PathVariable String mediaId) {
    ProfilePictureResponse response = userService.attachCurrentUserProfilePicture(mediaId);
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), response, "Profile picture updated"));
  }
}
