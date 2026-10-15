package com.messaging.crm;

import com.messaging.common.response.ApiResponse;
import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.security.service.CurrentUserService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/crm/bookings")
@RequiredArgsConstructor
public class CrmBookingController {

  private final CrmBookingService bookingService;
  private final CompanyAuthorizationService authorization;
  private final CurrentUserService currentUser;

  @GetMapping("/types")
  public ResponseEntity<ApiResponse<List<CrmBookingService.BookingTypeDto>>> listBookingTypes(
      @PathVariable long companyId) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, bookingService.listBookingTypes(companyId), "Booking types fetched"));
  }

  @PostMapping("/types")
  public ResponseEntity<ApiResponse<CrmBookingService.BookingTypeDto>> createBookingType(
      @PathVariable long companyId,
      @RequestBody CrmBookingService.SaveBookingTypeInput input) {
    long userId = require(companyId, CompanyPermissionKeys.CRM_LEAD_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(200, bookingService.createBookingType(companyId, userId, input), "Booking type created"));
  }

  @PutMapping("/types/{id}")
  public ResponseEntity<ApiResponse<CrmBookingService.BookingTypeDto>> updateBookingType(
      @PathVariable long companyId,
      @PathVariable long id,
      @RequestBody CrmBookingService.SaveBookingTypeInput input) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_MODIFY);
    return ResponseEntity.ok(
        ApiResponse.success(200, bookingService.updateBookingType(companyId, id, input), "Booking type updated"));
  }

  @PatchMapping("/types/{id}/toggle")
  public ResponseEntity<ApiResponse<CrmBookingService.BookingTypeDto>> toggleBookingType(
      @PathVariable long companyId,
      @PathVariable long id,
      @RequestBody Map<String, Boolean> body) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_MODIFY);
    boolean active = body != null && Boolean.TRUE.equals(body.get("isActive"));
    return ResponseEntity.ok(
        ApiResponse.success(200, bookingService.toggleBookingType(companyId, id, active), "Booking type state updated"));
  }

  @DeleteMapping("/types/{id}")
  public ResponseEntity<ApiResponse<Map<String, Object>>> deleteBookingType(
      @PathVariable long companyId,
      @PathVariable long id) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_MODIFY);
    bookingService.deleteBookingType(companyId, id);
    return ResponseEntity.ok(
        ApiResponse.success(200, Map.of("success", true, "id", String.valueOf(id)), "Booking type deleted"));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<CrmBookingService.MeetingBookingDto>>> listBookings(
      @PathVariable long companyId,
      @RequestParam(required = false) Long bookingTypeId,
      @RequestParam(required = false) Long contactId,
      @RequestParam(defaultValue = "50") int limit) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_READ);
    return ResponseEntity.ok(
        ApiResponse.success(200, bookingService.listBookings(companyId, bookingTypeId, contactId, limit), "Bookings fetched"));
  }

  @PatchMapping("/{id}/cancel")
  public ResponseEntity<ApiResponse<Map<String, Object>>> cancelBooking(
      @PathVariable long companyId,
      @PathVariable long id) {
    require(companyId, CompanyPermissionKeys.CRM_LEAD_MODIFY);
    bookingService.cancelBooking(companyId, id);
    return ResponseEntity.ok(
        ApiResponse.success(200, Map.of("success", true, "id", String.valueOf(id)), "Booking cancelled"));
  }

  private long require(long companyId, String permission) {
    long userId = currentUser.currentUserId();
    authorization.requirePermission(userId, companyId, permission);
    return userId;
  }
}
