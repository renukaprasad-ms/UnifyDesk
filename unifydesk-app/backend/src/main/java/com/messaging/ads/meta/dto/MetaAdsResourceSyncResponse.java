package com.messaging.ads.meta.dto;

import com.messaging.ads.dto.AdAccountResourceResponse;
import java.util.List;

public record MetaAdsResourceSyncResponse(
    int pages,
    int instagramAccounts,
    int pixels,
    List<AdAccountResourceResponse> resources) {}
