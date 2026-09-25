package com.messaging.ads.meta.dto;

import java.util.List;

public record MetaInsightBreakdownsSyncResponse(
    int age,
    int gender,
    int country,
    int region,
    int publisherPlatform,
    int platformPosition,
    int impressionDevice,
    int devicePlatform,
    int conversionDestination,
    int actionType,
    int actionDevice,
    List<String> failedBreakdowns) {

  public int totalRows() {
    return age
        + gender
        + country
        + region
        + publisherPlatform
        + platformPosition
        + impressionDevice
        + devicePlatform
        + conversionDestination
        + actionType
        + actionDevice;
  }
}
