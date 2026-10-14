package com.messaging.crm;

import com.messaging.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public REST Controller for Web-to-Lead Forms.
 * Base path: /api/v1/public/crm/forms
 * No authentication required.
 */
@RestController
@RequestMapping("/api/v1/public/crm/forms")
@RequiredArgsConstructor
public class CrmPublicLeadFormController {

  private final CrmLeadFormService service;

  @GetMapping("/{formKey}")
  public ResponseEntity<ApiResponse<CrmLeadFormService.PublicFormDto>> getPublicForm(
      @PathVariable String formKey) {
    return ResponseEntity.ok(ApiResponse.success(200, service.getPublicForm(formKey), "Public form loaded"));
  }

  @PostMapping("/{formKey}/submit")
  public ResponseEntity<ApiResponse<CrmLeadFormService.SubmitResult>> submitForm(
      @PathVariable String formKey,
      @RequestBody Map<String, Object> payload,
      HttpServletRequest request) {
    String clientIp = resolveClientIp(request);
    String userAgent = request.getHeader("User-Agent");
    if (userAgent != null && userAgent.length() > 500) {
      userAgent = userAgent.substring(0, 500);
    }
    CrmLeadFormService.SubmitResult result =
        service.submitPublicForm(formKey, payload, clientIp, userAgent);
    return ResponseEntity.ok(ApiResponse.success(200, result, "Form submitted successfully"));
  }

  private String resolveClientIp(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
      return xForwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
