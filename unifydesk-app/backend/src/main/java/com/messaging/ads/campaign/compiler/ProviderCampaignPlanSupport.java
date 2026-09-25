package com.messaging.ads.campaign.compiler;

import com.messaging.ads.capability.AdFeatureCapability;
import com.messaging.ads.capability.AdFeatureCapabilityResponse;
import com.messaging.ads.campaign.entity.BudgetPlan;
import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.enums.BudgetAllocationMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ProviderCampaignPlanSupport {

  private ProviderCampaignPlanSupport() {}

  static Long effectiveBudget(
      BudgetPlan plan, CampaignChannel channel, List<CampaignChannel> allChannels, List<ProviderPlanIssue> issues) {
    if (plan == null) {
      issues.add(error("BUDGET_MISSING", "budget", "Campaign budget plan is required."));
      return null;
    }

    if (plan.getAllocationMode() == BudgetAllocationMode.FIXED) {
      Long value = channel.getBudgetAllocationMinor();
      if (value == null || value <= 0) {
        issues.add(
            error(
                "CHANNEL_BUDGET_MISSING",
                "budget",
                "Fixed allocation requires a positive budget for this channel."));
        return null;
      }
      return value;
    }

    if (plan.getAllocationMode() == BudgetAllocationMode.PERCENTAGE) {
      BigDecimal percent = channel.getAllocationPercent();
      if (percent == null || percent.signum() <= 0) {
        issues.add(
            error(
                "CHANNEL_PERCENT_MISSING",
                "budget",
                "Percentage allocation requires a positive percentage for this channel."));
        return null;
      }
      return BigDecimal.valueOf(plan.getTotalBudgetMinor())
          .multiply(percent)
          .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
          .longValueExact();
    }

    long enabledCount = allChannels.stream().filter(CampaignChannel::isEnabled).count();
    if (enabledCount == 1) return plan.getTotalBudgetMinor();

    issues.add(
        error(
            "AUTOMATIC_ALLOCATION_UNRESOLVED",
            "budget",
            "Automatic allocation across multiple channels requires the future budget allocator before deployment."));
    return null;
  }

  static void requireCapability(
      ProviderCampaignCompileContext context,
      AdFeatureCapability feature,
      Set<AdFeatureCapability> required,
      List<ProviderPlanIssue> issues) {
    required.add(feature);
    AdFeatureCapabilityResponse capability = context.capabilities().get(feature);
    if (capability == null || !capability.supported()) {
      String reason =
          capability == null || capability.reason() == null
              ? "Required provider capability is unavailable."
              : capability.reason();
      issues.add(error("CAPABILITY_UNAVAILABLE", "capabilities." + feature.name(), reason));
    } else if (capability.accountEligible() == null) {
      issues.add(
          warning(
              "CAPABILITY_ELIGIBILITY_UNVERIFIED",
              "capabilities." + feature.name(),
              capability.reason() == null
                  ? "Provider eligibility will be verified during deployment."
                  : capability.reason()));
    }
  }

  static boolean requiresConversion(ProviderCampaignCompileContext context) {
    return switch (context.campaign().getBusinessGoal()) {
      case TRAFFIC, AWARENESS -> false;
      default -> true;
    };
  }

  static boolean hasErrors(List<ProviderPlanIssue> issues) {
    return issues.stream().anyMatch(issue -> issue.severity() == PlanIssueSeverity.ERROR);
  }

  static ProviderPlanIssue error(String code, String field, String message) {
    return new ProviderPlanIssue(PlanIssueSeverity.ERROR, code, field, message);
  }

  static ProviderPlanIssue warning(String code, String field, String message) {
    return new ProviderPlanIssue(PlanIssueSeverity.WARNING, code, field, message);
  }

  static Map<String, Object> config(Object... pairs) {
    Map<String, Object> config = new LinkedHashMap<>();
    for (int i = 0; i + 1 < pairs.length; i += 2) {
      Object value = pairs[i + 1];
      if (value != null) config.put(String.valueOf(pairs[i]), value);
    }
    return Map.copyOf(config);
  }
}
