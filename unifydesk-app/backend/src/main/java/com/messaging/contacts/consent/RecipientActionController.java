package com.messaging.contacts.consent;

import com.messaging.common.response.ApiResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/recipient-actions/email")
@RequiredArgsConstructor
public class RecipientActionController {

  private final RecipientEmailUnsubscribeService unsubscribeService;

  @PostMapping("/unsubscribe/{token}")
  public ResponseEntity<ApiResponse<?>> unsubscribe(@PathVariable String token) {
    try {
      unsubscribeService.unsubscribe(token);
      return ResponseEntity.ok(
          ApiResponse.success(
              HttpStatus.OK.value(),
              Map.of("status", "UNSUBSCRIBED"),
              "Unsubscribe request processed"));
    } catch (InvalidRecipientActionException exception) {
      return ResponseEntity.badRequest()
          .body(
              ApiResponse.error(
                  HttpStatus.BAD_REQUEST.value(),
                  "Unsubscribe link is invalid or expired"));
    }
  }
}
