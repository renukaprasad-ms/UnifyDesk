package com.messaging.crm;

import com.messaging.common.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/crm/bookings")
@RequiredArgsConstructor
public class CrmPublicBookingController {

  private final CrmBookingService bookingService;

  @GetMapping("/{slug}")
  public ResponseEntity<ApiResponse<CrmBookingService.PublicBookingTypeDto>> getPublicBookingType(
      @PathVariable String slug) {
    return ResponseEntity.ok(
        ApiResponse.success(200, bookingService.getPublicBookingType(slug), "Booking type fetched"));
  }

  @GetMapping("/{slug}/slots")
  public ResponseEntity<ApiResponse<List<String>>> getAvailableSlots(
      @PathVariable String slug,
      @RequestParam String date) {
    return ResponseEntity.ok(
        ApiResponse.success(200, bookingService.getAvailableSlots(slug, date), "Available slots fetched"));
  }

  @PostMapping("/{slug}/book")
  public ResponseEntity<ApiResponse<CrmBookingService.BookResult>> bookMeeting(
      @PathVariable String slug,
      @RequestBody CrmBookingService.BookMeetingInput input) {
    return ResponseEntity.ok(
        ApiResponse.success(200, bookingService.bookPublicMeeting(slug, input), "Meeting booked successfully"));
  }
}
