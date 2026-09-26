package com.messaging.ads.google.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GooglePerformanceMaxAutomationRequest(
    Boolean finalUrlExpansion,
    Boolean textAssetAutomation,
    Boolean enhancedYoutubeVideos,
    Boolean imageEnhancement,
    Boolean automatedVideoCrawl,
    @Valid @Size(max = 20) List<GoogleAutomatedVideoCrawlSource> automatedVideoCrawlSources,
    @Size(max = 500) List<@Size(max = 2048) String> urlExclusions) {}
