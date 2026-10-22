package com.messaging.emailprovider.product.console;

import com.messaging.emailprovider.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/console/v1/iam")
public class ConsoleRoleController {
  private final ConsoleRoleService roles;

  public ConsoleRoleController(ConsoleRoleService roles) {
    this.roles = roles;
  }

  public record Create(
      @NotBlank @Size(max = 80) String name,
      @NotNull Set<ConsolePermission> permissions,
      @NotBlank @Size(max = 128) String password,
      @Size(max = 80) String code) {}

  public record Update(
      @NotNull Set<ConsolePermission> permissions,
      @NotBlank @Size(max = 128) String password,
      @Size(max = 80) String code) {}

  public record Assign(
      @Positive Long roleId,
      @NotBlank @Size(max = 128) String password,
      @Size(max = 80) String code) {}

  @GetMapping("/roles")
  public ResponseEntity<ApiResponse<List<ConsoleRoleService.Role>>> list(HttpServletRequest req) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                200, roles.list(ConsoleAuthFilter.require(req)), "Organization roles"));
  }

  @GetMapping("/users")
  public ResponseEntity<ApiResponse<List<ConsoleRoleService.User>>> users(HttpServletRequest req) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                200, roles.users(ConsoleAuthFilter.require(req)), "Organization users"));
  }

  @PostMapping("/roles")
  public ApiResponse<ConsoleRoleService.Role> create(
      HttpServletRequest req, @Valid @RequestBody Create body) {
    return ApiResponse.success(
        201,
        roles.create(
            ConsoleAuthFilter.require(req),
            body.name(),
            body.permissions(),
            body.password(),
            body.code()),
        "Role created");
  }

  @PutMapping("/roles/{roleId}")
  public ApiResponse<Void> update(
      HttpServletRequest req,
      @PathVariable @Positive long roleId,
      @Valid @RequestBody Update body) {
    roles.update(
        ConsoleAuthFilter.require(req), roleId, body.permissions(), body.password(), body.code());
    return ApiResponse.success(200, "Role updated; affected sessions revoked");
  }

  @PutMapping("/users/{userId}/role")
  public ApiResponse<Void> assign(
      HttpServletRequest req,
      @PathVariable @Positive long userId,
      @Valid @RequestBody Assign body) {
    roles.assign(
        ConsoleAuthFilter.require(req), userId, body.roleId(), body.password(), body.code());
    return ApiResponse.success(200, "Role updated; affected sessions revoked");
  }
}
