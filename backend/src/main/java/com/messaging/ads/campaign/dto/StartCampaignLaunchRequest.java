package com.messaging.ads.campaign.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;

public record StartCampaignLaunchRequest(
    @NotBlank @Size(max = 160) String idempotencyKey,
    @NotEmpty List<@Valid LaunchChannelRequest> channels) {

  @AssertTrue(message = "Each campaign channel may appear only once in a launch request")
  public boolean hasUniqueChannels() {
    if (channels == null) return true;
    var ids = new HashSet<Long>();
    return channels.stream().allMatch(channel -> ids.add(channel.channelId()));
  }
}
