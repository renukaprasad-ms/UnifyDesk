package com.messaging.emailprovider.product.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class BulkEmailRequests {
  private BulkEmailRequests() {}

  /**
   * Bounded async bulk API. A batch creates per-chunk BCC-only messages, preventing accidental
   * disclosure of a whole marketing audience in visible To/Cc fields.
   */
  public record NewBatch(
      @NotNull @Positive Long sendingDomainId,
      @NotBlank @Email String from,
      @Email String replyTo,
      @NotBlank @Size(max = 998) String subject,
      String text,
      String html,
      @NotEmpty @Size(max = 1000) List<@NotBlank @Email @Size(max = 320) String> recipients,
      @NotBlank @Size(max = 128) String consentReference) {}
}
