package com.messaging.ads.optimization.dto;

import com.messaging.ads.enums.AdProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record FirstPartyAudienceBuildRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 1000) String description,
    Integer membershipDays,
    @NotEmpty List<@Valid Target> targets,
    @NotEmpty @Size(max = 50000) List<@Valid Member> members,
    boolean googleCustomerMatchTermsAccepted,
    boolean adUserDataConsent,
    boolean adPersonalizationConsent,
    boolean metaCustomAudienceTermsAccepted) {

  public record Target(
      @NotNull AdProvider provider,
      @NotNull Long adAccountId) {}

  public record Member(
      @Size(max = 320) String email,
      @Size(max = 32) String phone,
      @Size(max = 255) String externalId) {}
}
