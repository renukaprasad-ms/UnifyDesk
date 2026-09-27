package com.messaging.company.controller;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.dto.AcceptCompanyInvitationResponse;
import com.messaging.company.dto.CompanyInvitationResponse;
import com.messaging.company.dto.CreateCompanyInvitationRequest;
import com.messaging.company.service.CompanyInvitationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CompanyInvitationController {

  private final CompanyInvitationService invitationService;

  @PostMapping("/companies/{companyId}/invitations")
  public ResponseEntity<ApiResponse<CompanyInvitationResponse>> invite(
      @PathVariable Long companyId, @Valid @RequestBody CreateCompanyInvitationRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(),
                invitationService.invite(companyId, request),
                "Invitation sent"));
  }

  @GetMapping("/companies/{companyId}/invitations")
  public ResponseEntity<ApiResponse<List<CompanyInvitationResponse>>> invitations(
      @PathVariable Long companyId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            invitationService.invitations(companyId),
            "Invitations fetched"));
  }

  @GetMapping("/invitations/me")
  public ResponseEntity<ApiResponse<List<CompanyInvitationResponse>>> myInvitations() {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            invitationService.myInvitations(),
            "Invitations fetched"));
  }

  @PostMapping("/invitations/me/{invitationId}/accept")
  public ResponseEntity<ApiResponse<AcceptCompanyInvitationResponse>> acceptCurrentUserInvitation(
      @PathVariable Long invitationId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            invitationService.acceptCurrentUserInvitation(invitationId),
            "Invitation accepted"));
  }

  @PostMapping("/companies/{companyId}/invitations/{invitationId}/resend")
  public ResponseEntity<ApiResponse<CompanyInvitationResponse>> resend(
      @PathVariable Long companyId, @PathVariable Long invitationId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            invitationService.resend(companyId, invitationId),
            "Invitation resent"));
  }

  @DeleteMapping("/companies/{companyId}/invitations/{invitationId}")
  public ResponseEntity<ApiResponse<Void>> revoke(
      @PathVariable Long companyId, @PathVariable Long invitationId) {
    invitationService.revoke(companyId, invitationId);
    return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "Invitation revoked"));
  }

  @PostMapping("/invitations/{token}/accept")
  public ResponseEntity<ApiResponse<AcceptCompanyInvitationResponse>> accept(
      @PathVariable String token) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), invitationService.accept(token), "Invitation accepted"));
  }
}
