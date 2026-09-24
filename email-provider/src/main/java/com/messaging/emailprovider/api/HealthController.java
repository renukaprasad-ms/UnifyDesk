package com.messaging.emailprovider.api;

import com.messaging.emailprovider.api.dto.HealthResponse;
import com.messaging.emailprovider.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

  @GetMapping
  public ApiResponse<HealthResponse> health() {
    return ApiResponse.success(HttpStatus.OK.value(), HealthResponse.up(), "Service is healthy");
  }
}
