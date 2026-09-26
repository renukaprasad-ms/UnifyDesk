package com.messaging.ads.google.dto;

public record GoogleDemandGenSelectedChannels(
    Boolean youtubeInFeed,
    Boolean youtubeInStream,
    Boolean youtubeShorts,
    Boolean discover,
    Boolean gmail,
    Boolean display,
    Boolean maps) {}
