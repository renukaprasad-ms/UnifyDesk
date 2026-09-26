package com.messaging.ads.google.dto;

import java.util.List;
import java.util.Map;

public record GooglePerformanceMaxAutomationResponse(
    Map<String, String> automationStatuses,
    List<GoogleAutomatedVideoCrawlSource> automatedVideoCrawlSources,
    List<String> urlExclusions) {

  public GooglePerformanceMaxAutomationResponse {
    automationStatuses =
        automationStatuses == null ? Map.of() : Map.copyOf(automationStatuses);
    automatedVideoCrawlSources =
        automatedVideoCrawlSources == null
            ? List.of()
            : List.copyOf(automatedVideoCrawlSources);
    urlExclusions = urlExclusions == null ? List.of() : List.copyOf(urlExclusions);
  }
}
