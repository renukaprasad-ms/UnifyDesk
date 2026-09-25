package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GoogleReusableAssetCreateRequest(
    @NotNull Type type,
    @Size(max = 128) String name,
    @Size(max = 25) String linkText,
    @Size(max = 35) String description1,
    @Size(max = 35) String description2,
    @Size(max = 25) String calloutText,
    @Size(max = 64) String structuredSnippetHeader,
    @Size(max = 10) List<@Size(max = 25) String> structuredSnippetValues,
    @Size(max = 2048) String finalUrl,
    @Size(max = 2048) String finalMobileUrl,
    @Size(max = 2048) String trackingUrlTemplate,
    @Size(max = 2048) String finalUrlSuffix,
    @Size(max = 8) List<GoogleUrlCustomParameter> customParameters) {

  public enum Type {
    SITELINK,
    CALLOUT,
    STRUCTURED_SNIPPET
  }
}
