package com.messaging.emailprovider.mailbox.inbound;

import com.messaging.emailprovider.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Disabled by default. The existing InternalApiTokenFilter authenticates this
 * private endpoint; never route /internal/v1/ from a public proxy.
 */
@RestController
@ConditionalOnProperty(prefix = "app.mail.inbound", name = "enabled", havingValue = "true")
public class InboundIntakeController {
  private final InboundIntakeService intake;

  public InboundIntakeController(InboundIntakeService intake) {
    this.intake = intake;
  }

  @PostMapping(value = "/internal/v1/mail/inbound", consumes = "message/rfc822")
  public ResponseEntity<ApiResponse<InboundIntakeService.Intake>> receive(
      HttpServletRequest request,
      @RequestHeader("X-UnifyDesk-Envelope-To") String envelopeTo,
      @RequestHeader("X-UnifyDesk-Delivery-Key") String deliveryKey) throws IOException {
    InboundIntakeService.Intake job = intake.accept(
        envelopeTo, deliveryKey, request.getInputStream());
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success(202, job, "MIME durably staged for security scanning"));
  }
}
