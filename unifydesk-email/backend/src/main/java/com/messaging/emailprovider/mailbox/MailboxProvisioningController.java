package com.messaging.emailprovider.mailbox;

import com.messaging.emailprovider.common.exception.ForbiddenException;
import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.product.console.ConsoleAuthFilter;
import com.messaging.emailprovider.product.console.ConsoleAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Console-session-only staging endpoints; no mailbox activation. */
@Validated
@RestController
@RequestMapping("/console/v1/mail")
public class MailboxProvisioningController {
  private final MailboxProvisioningService service;

  public MailboxProvisioningController(MailboxProvisioningService service) {
    this.service = service;
  }

  public record NewDomain(@NotBlank @Size(max = 253) String domain) {}

  public record NewMailbox(
      @NotBlank @Size(max = 64) String localPart, @Size(max = 160) String displayName) {}

  @GetMapping("/domains")
  public ApiResponse<List<MailboxProvisioningService.Domain>> domains(HttpServletRequest req) {
    return ApiResponse.success(200, service.domains(user(req).organizationId()), "Mail domains");
  }

  @PostMapping("/domains")
  public ResponseEntity<ApiResponse<MailboxProvisioningService.DomainChallenge>> createDomain(
      HttpServletRequest req, @Valid @RequestBody NewDomain body) {
    var principal = manager(req);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                service.registerDomain(principal.organizationId(), body.domain()),
                "Domain staged pending ownership verification"));
  }

  @PostMapping("/domains/{domainId}/challenge")
  public ApiResponse<MailboxProvisioningService.DomainChallenge> rotateChallenge(
      HttpServletRequest req, @PathVariable @Positive long domainId) {
    var principal = manager(req);
    return ApiResponse.success(
        200,
        service.rotateChallenge(principal.organizationId(), domainId),
        "Publish the new TXT value; previous challenge is invalid");
  }

  @PostMapping("/domains/{domainId}/verify")
  public ApiResponse<MailboxProvisioningService.Domain> verifyDomain(
      HttpServletRequest req, @PathVariable @Positive long domainId) {
    var principal = manager(req);
    return ApiResponse.success(
        200,
        service.verifyDomain(principal.organizationId(), domainId),
        "Mail domain ownership verified; inbound mail remains disabled");
  }

  @GetMapping("/domains/{domainId}/mailboxes")
  public ApiResponse<List<MailboxProvisioningService.Mailbox>> mailboxes(
      HttpServletRequest req, @PathVariable @Positive long domainId) {
    return ApiResponse.success(
        200, service.mailboxes(user(req).organizationId(), domainId), "Mailboxes");
  }

  @PostMapping("/domains/{domainId}/mailboxes")
  public ResponseEntity<ApiResponse<MailboxProvisioningService.Mailbox>> createMailbox(
      HttpServletRequest req,
      @PathVariable @Positive long domainId,
      @Valid @RequestBody NewMailbox body) {
    var principal = manager(req);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                service.createMailbox(
                    principal.organizationId(), domainId, body.localPart(), body.displayName()),
                "Mailbox staged disabled until verification and security QA"));
  }

  private static ConsoleAuthService.Principal user(HttpServletRequest req) {
    return ConsoleAuthFilter.require(req);
  }

  private static ConsoleAuthService.Principal manager(HttpServletRequest req) {
    var principal = user(req);
    if (!principal.role().canManage()) {
      throw new ForbiddenException("Admin or owner permission required");
    }
    return principal;
  }
}
