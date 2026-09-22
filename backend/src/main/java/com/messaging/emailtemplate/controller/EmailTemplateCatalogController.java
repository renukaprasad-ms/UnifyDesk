package com.messaging.emailtemplate.controller;

import com.messaging.common.response.ApiResponse;
import com.messaging.emailtemplate.dto.EmailTemplateCatalogResponse;
import com.messaging.emailtemplate.enums.EmailTemplateCategory;
import com.messaging.emailtemplate.service.EmailTemplateCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/email-template-catalog")
@RequiredArgsConstructor
public class EmailTemplateCatalogController {
  private final EmailTemplateCatalogService service;

  @GetMapping
  public ResponseEntity<ApiResponse<List<EmailTemplateCatalogResponse>>> list(
      @RequestParam(required = false) EmailTemplateCategory category) {
    return ResponseEntity.ok(ApiResponse.success(200, service.list(category), "Email template catalog fetched"));
  }

  @GetMapping("/{catalogId}")
  public ResponseEntity<ApiResponse<EmailTemplateCatalogResponse>> get(@PathVariable Long catalogId) {
    return ResponseEntity.ok(ApiResponse.success(200, service.get(catalogId), "Catalog template fetched"));
  }
}
