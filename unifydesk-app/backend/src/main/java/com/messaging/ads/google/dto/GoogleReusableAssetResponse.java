package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleReusableAssetResponse(
    String id,
    String resourceName,
    String name,
    String type,
    String source,
    String linkText,
    String description1,
    String description2,
    String calloutText,
    String structuredSnippetHeader,
    List<String> structuredSnippetValues,
    List<String> finalUrls,
    List<String> finalMobileUrls,
    String trackingUrlTemplate,
    String finalUrlSuffix,
    List<GoogleUrlCustomParameter> customParameters,
    String text,
    String imageUrl,
    Long imageWidth,
    Long imageHeight) {

  public GoogleReusableAssetResponse(
      String id,
      String resourceName,
      String name,
      String type,
      String source,
      String linkText,
      String description1,
      String description2,
      String calloutText,
      String structuredSnippetHeader,
      List<String> structuredSnippetValues,
      List<String> finalUrls,
      List<String> finalMobileUrls,
      String trackingUrlTemplate,
      String finalUrlSuffix,
      List<GoogleUrlCustomParameter> customParameters) {
    this(
        id,
        resourceName,
        name,
        type,
        source,
        linkText,
        description1,
        description2,
        calloutText,
        structuredSnippetHeader,
        structuredSnippetValues,
        finalUrls,
        finalMobileUrls,
        trackingUrlTemplate,
        finalUrlSuffix,
        customParameters,
        null,
        null,
        null,
        null);
  }

  public GoogleReusableAssetResponse {
    structuredSnippetValues =
        structuredSnippetValues == null ? List.of() : List.copyOf(structuredSnippetValues);
    finalUrls = finalUrls == null ? List.of() : List.copyOf(finalUrls);
    finalMobileUrls = finalMobileUrls == null ? List.of() : List.copyOf(finalMobileUrls);
    customParameters =
        customParameters == null ? List.of() : List.copyOf(customParameters);
  }
}
