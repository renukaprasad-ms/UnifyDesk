package com.messaging.emailprovider.product.api;

import com.messaging.emailprovider.common.response.ApiResponse;
import com.messaging.emailprovider.product.auth.EmailProductKeyFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmailProductProjectController {
  public record Status(String projectId, boolean sendingEnabled) {}

  @GetMapping("/v1/project")
  public ApiResponse<Status> status(HttpServletRequest request) {
    var project = EmailProductKeyFilter.require(request);
    return ApiResponse.success(200,
        new Status(project.projectId().toString(), project.sendingEnabled()),
        "Email project availability");
  }
}
