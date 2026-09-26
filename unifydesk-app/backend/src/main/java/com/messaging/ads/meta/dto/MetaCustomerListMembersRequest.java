package com.messaging.ads.meta.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MetaCustomerListMembersRequest(
    @NotEmpty @Size(max = 16) List<@Size(max = 32) String> schema,
    @NotEmpty @Size(max = 50000) List<@NotEmpty List<@Size(max = 512) String>> rows,
    @NotNull Operation operation,
    Boolean preHashed,
    @NotNull Boolean customAudienceTermsAccepted) {

  public enum Operation {
    ADD,
    REMOVE
  }

  @AssertTrue(message = "Meta Custom Audience terms and first-party data authorization must be confirmed")
  public boolean termsConfirmed() {
    return Boolean.TRUE.equals(customAudienceTermsAccepted);
  }
}
