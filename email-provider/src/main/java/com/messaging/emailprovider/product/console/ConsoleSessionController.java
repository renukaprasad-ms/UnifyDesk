package com.messaging.emailprovider.product.console;

import com.messaging.emailprovider.common.exception.ForbiddenException;
import com.messaging.emailprovider.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/console/v1")
public class ConsoleSessionController {
  private final ConsoleAuthService auth;

  public ConsoleSessionController(ConsoleAuthService auth) {
    this.auth = auth;
  }

  public record Credentials(@NotNull @Positive Long organizationId,
                            @Email @NotBlank String email,
                            @NotBlank String password) {}
  public record Activation(@NotBlank String invitationToken,
                           @NotBlank @Size(min = 12, max = 128) String password) {}

  @PostMapping("/activate")
  public ApiResponse<Void> activate(@Valid @RequestBody Activation request) {
    auth.activate(request.invitationToken(), request.password());
    return ApiResponse.success(200, "Account activated");
  }

  @PostMapping("/sessions")
  public ApiResponse<ConsoleAuthService.Login> login(
      HttpServletRequest context, @Valid @RequestBody Credentials request) {
    return ApiResponse.success(200, auth.login(request.organizationId(),
        request.email(), request.password(), context.getRemoteAddr()), "Signed in");
  }

  @GetMapping("/me")
  public ApiResponse<ConsoleAuthService.Principal> me(HttpServletRequest context) {
    return ApiResponse.success(200, ConsoleAuthFilter.require(context), "Account");
  }

  @DeleteMapping("/sessions")
  public ApiResponse<Void> logout(HttpServletRequest context) {
    auth.logout(ConsoleAuthFilter.require(context));
    return ApiResponse.success(200, "Signed out");
  }
}
