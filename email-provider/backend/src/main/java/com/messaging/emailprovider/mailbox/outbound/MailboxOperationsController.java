package com.messaging.emailprovider.mailbox.outbound;

import com.messaging.emailprovider.common.response.ApiResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Private diagnostics endpoint behind the internal service bearer filter. */
@RestController
@ConditionalOnProperty(prefix="app.mail.ops",name="enabled",havingValue="true")
@RequestMapping("/internal/v1/mail/outbound")
public class MailboxOperationsController {
  private final MailboxOperationsService operations;

  public MailboxOperationsController(MailboxOperationsService operations) {
    this.operations=operations;
  }

  @GetMapping("/reconciliation")
  public ResponseEntity<ApiResponse<List<MailboxOperationsService.UnknownAttempt>>> unknown() {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(200,operations.unknownAttempts(),
            "Uncertain SMTP handoffs require operator verification; no automatic retry"));
  }

  @GetMapping("/ops")
  public ResponseEntity<ApiResponse<MailboxOperationsService.Snapshot>> snapshot() {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(200,operations.snapshot(),
            "Mail operations snapshot"));
  }
}
