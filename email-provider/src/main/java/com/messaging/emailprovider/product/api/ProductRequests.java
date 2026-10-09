package com.messaging.emailprovider.product.api;

import com.messaging.emailprovider.product.auth.ApiKeyScope;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;

public final class ProductRequests {
  private ProductRequests() {}

  public record NewOrganization(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Email @Size(max = 320) String contactEmail) {}

  public record NewProject(@NotBlank @Size(max = 120) String name) {}

  public record NewKey(
      @NotBlank @Size(max = 100) String name,
      @NotEmpty Set<@NotNull ApiKeyScope> scopes,
      @Future Instant expiresAt) {}

  public record SendingSwitch(boolean enabled) {}

  public record StatusSwitch(
      @NotNull com.messaging.emailprovider.product.model.ProductStatus status) {}
}
