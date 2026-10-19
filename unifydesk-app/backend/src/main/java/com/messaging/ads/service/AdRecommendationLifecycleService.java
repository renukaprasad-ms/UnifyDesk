package com.messaging.ads.service;

import com.messaging.ads.dto.AdRecommendationSummaryResponse;
import com.messaging.ads.dto.AdRecommendationsResponse;
import com.messaging.ads.dto.UpdateAdRecommendationStateRequest;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdRecommendationState;
import com.messaging.ads.enums.AdRecommendationLifecycleAction;
import com.messaging.ads.enums.AdRecommendationLifecycleStatus;
import com.messaging.ads.enums.AdRecommendationSeverity;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdRecommendationStateRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdRecommendationLifecycleService {

  private static final Duration MAX_CRITICAL_SNOOZE = Duration.ofHours(24);
  private static final Duration MAX_GENERAL_SNOOZE = Duration.ofDays(30);

  private final AdRecommendationService recommendationService;
  private final AdRecommendationStateRepository stateRepository;
  private final AdAccountRepository accountRepository;
  private final AdActivityLogService activityLogService;

  @Transactional(readOnly = true)
  public AdRecommendationsResponse feed(Long companyId, Long accountId) {
    AdAccount account = requireAccount(companyId, accountId);
    AdRecommendationsResponse raw = recommendationService.recommendations(companyId, accountId);
    return overlay(account, raw);
  }

  @Transactional(readOnly = true)
  public AdRecommendationSummaryResponse summary(Long companyId) {
    List<AdAccount> accounts =
        accountRepository.findByCompanyIdOrderByProviderAscNameAsc(companyId).stream()
            .filter(AdAccount::isAccessible)
            .filter(AdAccount::isManaged)
            .toList();

    int critical = 0;
    int warning = 0;
    int opportunity = 0;
    int info = 0;
    int dismissed = 0;
    int snoozed = 0;
    List<AdRecommendationSummaryResponse.AccountItem> top = new ArrayList<>();

    for (AdAccount account : accounts) {
      AdRecommendationsResponse feed = feed(companyId, account.getId());
      critical += feed.criticalCount();
      warning += feed.warningCount();
      opportunity += feed.opportunityCount();
      info += feed.infoCount();
      dismissed += feed.dismissedCount();
      snoozed += feed.snoozedCount();

      for (AdRecommendationsResponse.Item item : feed.items()) {
        top.add(
            new AdRecommendationSummaryResponse.AccountItem(
                account.getId().toString(), account.getName(), item));
      }
    }

    top.sort(
        Comparator.comparingInt(
                (AdRecommendationSummaryResponse.AccountItem item) ->
                    severityRank(item.item().severity()))
            .thenComparing(item -> item.accountName() == null ? "" : item.accountName())
            .thenComparing(item -> item.item().title()));

    return new AdRecommendationSummaryResponse(
        accounts.size(),
        critical,
        warning,
        opportunity,
        info,
        critical + warning,
        dismissed,
        snoozed,
        top.stream().limit(5).toList());
  }

  @Transactional
  public AdRecommendationsResponse reconcile(Long companyId, Long accountId, Long actorUserId) {
    AdAccount account = requireAccount(companyId, accountId);
    AdRecommendationsResponse raw = recommendationService.recommendations(companyId, accountId);

    Instant now = Instant.now();
    Map<String, AdRecommendationState> existing =
        stateRepository.findByCompanyIdAndAdAccountId(companyId, accountId).stream()
            .collect(
                Collectors.toMap(
                    AdRecommendationState::getRecommendationKey,
                    state -> state,
                    (first, ignored) -> first,
                    HashMap::new));

    Set<String> currentKeys =
        raw.items().stream().map(AdRecommendationsResponse.Item::key).collect(Collectors.toSet());

    List<AdRecommendationState> changed = new ArrayList<>();

    for (AdRecommendationsResponse.Item item : raw.items()) {
      String fingerprint = fingerprint(item);
      AdRecommendationState state = existing.get(item.key());

      if (state == null) {
        state = new AdRecommendationState();
        state.setCompanyId(companyId);
        state.setAdAccountId(accountId);
        state.setProvider(account.getProvider());
        state.setRecommendationKey(item.key());
        state.setFingerprint(fingerprint);
        state.setStatus(AdRecommendationLifecycleStatus.ACTIVE);
        state.setLastSeenAt(now);
        changed.add(state);
        existing.put(item.key(), state);
        continue;
      }

      boolean fingerprintChanged = !fingerprint.equals(state.getFingerprint());
      boolean snoozeExpired =
          state.getStatus() == AdRecommendationLifecycleStatus.SNOOZED
              && (state.getSnoozedUntil() == null || !state.getSnoozedUntil().isAfter(now));

      if (fingerprintChanged
          || state.getStatus() == AdRecommendationLifecycleStatus.RESOLVED
          || snoozeExpired) {
        state.setFingerprint(fingerprint);
        state.setStatus(AdRecommendationLifecycleStatus.ACTIVE);
        state.setSnoozedUntil(null);
        state.setResolvedAt(null);
      }

      state.setLastSeenAt(now);
      changed.add(state);
    }

    for (AdRecommendationState state : existing.values()) {
      if (currentKeys.contains(state.getRecommendationKey())) continue;
      if (state.getStatus() == AdRecommendationLifecycleStatus.RESOLVED) continue;

      AdRecommendationLifecycleStatus before = state.getStatus();
      state.setStatus(AdRecommendationLifecycleStatus.RESOLVED);
      state.setResolvedAt(now);
      state.setSnoozedUntil(null);
      state.setActorUserId(actorUserId);
      changed.add(state);

      activityLogService.record(
          companyId,
          accountId,
          account.getProvider(),
          actorUserId,
          "AD_RECOMMENDATION_AUTO_RESOLVED",
          "AD_RECOMMENDATION",
          state.getRecommendationKey(),
          Map.of("status", before.name()),
          Map.of("status", AdRecommendationLifecycleStatus.RESOLVED.name()),
          Map.of());
    }

    stateRepository.saveAll(changed);
    return overlay(account, raw);
  }

  @Transactional
  public AdRecommendationsResponse updateState(
      Long companyId,
      Long accountId,
      String recommendationKey,
      UpdateAdRecommendationStateRequest request,
      Long actorUserId) {
    AdAccount account = requireAccount(companyId, accountId);
    AdRecommendationsResponse raw = recommendationService.recommendations(companyId, accountId);
    AdRecommendationsResponse.Item item =
        raw.items().stream()
            .filter(candidate -> candidate.key().equals(recommendationKey))
            .findFirst()
            .orElseThrow(
                () ->
                    new BadRequestException(
                        "Recommendation is no longer active for this ad account"));

    Instant now = Instant.now();
    String fingerprint = fingerprint(item);
    AdRecommendationState state =
        stateRepository
            .findByCompanyIdAndAdAccountIdAndProviderAndRecommendationKey(
                companyId, accountId, account.getProvider(), recommendationKey)
            .orElseGet(
                () -> {
                  AdRecommendationState created = new AdRecommendationState();
                  created.setCompanyId(companyId);
                  created.setAdAccountId(accountId);
                  created.setProvider(account.getProvider());
                  created.setRecommendationKey(recommendationKey);
                  created.setFingerprint(fingerprint);
                  created.setStatus(AdRecommendationLifecycleStatus.ACTIVE);
                  created.setLastSeenAt(now);
                  return created;
                });

    AdRecommendationLifecycleStatus before = state.getStatus();
    state.setFingerprint(fingerprint);
    state.setActorUserId(actorUserId);
    state.setLastSeenAt(now);
    state.setResolvedAt(null);

    AdRecommendationLifecycleAction action = request.action();
    switch (action) {
      case DISMISS -> dismiss(item, state);
      case SNOOZE -> snooze(item, state, request.snoozedUntil(), now);
      case REOPEN -> reopen(state);
    }

    stateRepository.save(state);

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("severity", item.severity().name());
    metadata.put("category", item.category().name());
    if (state.getSnoozedUntil() != null) {
      metadata.put("snoozedUntil", state.getSnoozedUntil().toString());
    }

    activityLogService.record(
        companyId,
        accountId,
        account.getProvider(),
        actorUserId,
        activityAction(action),
        "AD_RECOMMENDATION",
        recommendationKey,
        Map.of("status", before.name()),
        Map.of("status", state.getStatus().name()),
        metadata);

    return feed(companyId, accountId);
  }

  private void dismiss(AdRecommendationsResponse.Item item, AdRecommendationState state) {
    if (item.severity() == AdRecommendationSeverity.CRITICAL) {
      throw new BadRequestException(
          "Critical recommendations cannot be dismissed; resolve the source issue or snooze it briefly");
    }
    state.setStatus(AdRecommendationLifecycleStatus.DISMISSED);
    state.setSnoozedUntil(null);
  }

  private void snooze(
      AdRecommendationsResponse.Item item,
      AdRecommendationState state,
      Instant snoozedUntil,
      Instant now) {
    if (snoozedUntil == null || !snoozedUntil.isAfter(now)) {
      throw new BadRequestException("Snooze end time must be in the future");
    }

    Duration requested = Duration.between(now, snoozedUntil);
    Duration max =
        item.severity() == AdRecommendationSeverity.CRITICAL
            ? MAX_CRITICAL_SNOOZE
            : MAX_GENERAL_SNOOZE;

    if (requested.compareTo(max) > 0) {
      throw new BadRequestException(
          item.severity() == AdRecommendationSeverity.CRITICAL
              ? "Critical recommendations can be snoozed for at most 24 hours"
              : "Recommendations can be snoozed for at most 30 days");
    }

    state.setStatus(AdRecommendationLifecycleStatus.SNOOZED);
    state.setSnoozedUntil(snoozedUntil);
  }

  private void reopen(AdRecommendationState state) {
    state.setStatus(AdRecommendationLifecycleStatus.ACTIVE);
    state.setSnoozedUntil(null);
  }

  private AdRecommendationsResponse overlay(AdAccount account, AdRecommendationsResponse raw) {
    Instant now = Instant.now();
    Map<String, AdRecommendationState> states =
        stateRepository
            .findByCompanyIdAndAdAccountId(account.getCompanyId(), account.getId())
            .stream()
            .collect(
                Collectors.toMap(
                    AdRecommendationState::getRecommendationKey,
                    state -> state,
                    (first, ignored) -> first));

    List<AdRecommendationsResponse.Item> visible = new ArrayList<>();
    List<AdRecommendationsResponse.Item> suppressed = new ArrayList<>();

    for (AdRecommendationsResponse.Item item : raw.items()) {
      AdRecommendationState state = states.get(item.key());
      String fingerprint = fingerprint(item);

      if (state == null || !fingerprint.equals(state.getFingerprint())) {
        visible.add(withLifecycle(item, AdRecommendationLifecycleStatus.ACTIVE, null));
        continue;
      }

      if (state.getStatus() == AdRecommendationLifecycleStatus.DISMISSED) {
        suppressed.add(withLifecycle(item, AdRecommendationLifecycleStatus.DISMISSED, null));
        continue;
      }

      if (state.getStatus() == AdRecommendationLifecycleStatus.SNOOZED
          && state.getSnoozedUntil() != null
          && state.getSnoozedUntil().isAfter(now)) {
        suppressed.add(
            withLifecycle(item, AdRecommendationLifecycleStatus.SNOOZED, state.getSnoozedUntil()));
        continue;
      }

      visible.add(withLifecycle(item, AdRecommendationLifecycleStatus.ACTIVE, null));
    }

    return new AdRecommendationsResponse(
        raw.adAccountId(),
        raw.metaOpportunityScore(),
        raw.metaOpportunityScoreWeight(),
        count(visible, AdRecommendationSeverity.CRITICAL),
        count(visible, AdRecommendationSeverity.WARNING),
        count(visible, AdRecommendationSeverity.OPPORTUNITY),
        count(visible, AdRecommendationSeverity.INFO),
        (int)
            suppressed.stream()
                .filter(item -> item.lifecycleStatus() == AdRecommendationLifecycleStatus.DISMISSED)
                .count(),
        (int)
            suppressed.stream()
                .filter(item -> item.lifecycleStatus() == AdRecommendationLifecycleStatus.SNOOZED)
                .count(),
        raw.generatedAt(),
        raw.healthLastSyncAt(),
        raw.billingLastSyncAt(),
        raw.analyticsLastSyncAt(),
        List.copyOf(visible),
        List.copyOf(suppressed));
  }

  private AdRecommendationsResponse.Item withLifecycle(
      AdRecommendationsResponse.Item item,
      AdRecommendationLifecycleStatus status,
      Instant snoozedUntil) {
    return new AdRecommendationsResponse.Item(
        item.key(),
        item.severity(),
        item.category(),
        item.source(),
        item.title(),
        item.description(),
        item.entityType(),
        item.entityId(),
        item.actionLabel(),
        item.actionPath(),
        item.evidence(),
        status,
        snoozedUntil,
        item.severity() != AdRecommendationSeverity.CRITICAL,
        true);
  }

  private int count(List<AdRecommendationsResponse.Item> items, AdRecommendationSeverity severity) {
    return (int) items.stream().filter(item -> item.severity() == severity).count();
  }

  private String fingerprint(AdRecommendationsResponse.Item item) {
    String material =
        String.join(
            "|",
            item.key(),
            item.severity().name(),
            item.source().name(),
            item.entityType() == null ? "" : item.entityType(),
            item.entityId() == null ? "" : item.entityId());
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(hash);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to fingerprint recommendation", exception);
    }
  }

  private String activityAction(AdRecommendationLifecycleAction action) {
    return switch (action) {
      case DISMISS -> "AD_RECOMMENDATION_DISMISSED";
      case SNOOZE -> "AD_RECOMMENDATION_SNOOZED";
      case REOPEN -> "AD_RECOMMENDATION_REOPENED";
    };
  }

  private int severityRank(AdRecommendationSeverity severity) {
    return switch (severity) {
      case CRITICAL -> 0;
      case WARNING -> 1;
      case OPPORTUNITY -> 2;
      case INFO -> 3;
    };
  }

  private AdAccount requireAccount(Long companyId, Long accountId) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    return account;
  }
}
