package com.messaging.ads.campaign.launch;

import java.util.Map;

public record LaunchStepExecutionResult(
    LaunchStepStatus status,
    Long localResourceId,
    String externalResourceId,
    Map<String, Object> response) {

  public static LaunchStepExecutionResult succeeded(
      Long localResourceId, String externalResourceId, Map<String, Object> response) {
    return new LaunchStepExecutionResult(
        LaunchStepStatus.SUCCEEDED, localResourceId, externalResourceId, response);
  }

  public static LaunchStepExecutionResult skipped(String reason) {
    return new LaunchStepExecutionResult(
        LaunchStepStatus.SKIPPED, null, null, Map.of("reason", reason));
  }
}
