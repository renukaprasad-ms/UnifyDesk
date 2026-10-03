package com.messaging.broadcast.transport;

import java.util.Map;

public record BroadcastEmailComplianceMetadata(
    String visibleUnsubscribeUrl, String oneClickUnsubscribeUrl) {

  public Map<String, String> standardOneClickHeaders() {
    return Map.of(
        "List-Unsubscribe", "<" + oneClickUnsubscribeUrl + ">",
        "List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
  }
}
