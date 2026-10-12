package com.messaging.emailprovider.product.webhook;

import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.product.auth.EmailProductKeyFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/webhook-endpoints")
public class EmailProductWebhookController {
  private final WebhookService webhooks;
  public EmailProductWebhookController(WebhookService webhooks) { this.webhooks = webhooks; }

  public record NewWebhook(@NotBlank @Size(max=1024) String url) {}

  @PostMapping
  public ResponseEntity<ApiResponse<WebhookService.Created>> create(
      HttpServletRequest request, @Valid @RequestBody NewWebhook body) {
    Long projectId = EmailProductKeyFilter.require(request).projectId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(201, webhooks.create(projectId, body.url()),
            "Store the signing secret now; it will not be returned again"));
  }

  @GetMapping
  public ApiResponse<List<WebhookService.View>> list(HttpServletRequest request) {
    return ApiResponse.success(200,
        webhooks.list(EmailProductKeyFilter.require(request).projectId()), "Webhook endpoints");
  }

  @DeleteMapping("/{endpointId}")
  public ApiResponse<Void> disable(HttpServletRequest request,
      @PathVariable @Positive Long endpointId) {
    webhooks.disable(EmailProductKeyFilter.require(request).projectId(), endpointId);
    return ApiResponse.success(200, "Webhook disabled");
  }
}
