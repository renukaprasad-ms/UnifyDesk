package com.messaging.ads.optimization;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdOptimizationScheduler {

  private final AdOptimizationPolicyService policyService;

  @Scheduled(cron = "${app.ads.optimization.policy-cron:0 15 * * * *}")
  public void evaluatePolicies() {
    policyService.evaluateEnabledAutomatically();
  }
}
