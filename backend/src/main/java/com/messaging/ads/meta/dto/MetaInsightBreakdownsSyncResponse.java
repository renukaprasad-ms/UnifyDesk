package com.messaging.ads.meta.dto;

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
    int actionDevice) {

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
        + actionDevice;
  }
}
