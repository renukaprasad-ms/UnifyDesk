package com.messaging.ads.google;

import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleAdsBackgroundSyncScheduler {

  private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT =
      new DefaultRedisScript<>(
          "if redis.call('get', KEYS[1]) == ARGV[1] then "
              + "return redis.call('del', KEYS[1]) else return 0 end",
          Long.class);

  private final GoogleAdsProperties properties;
  private final AdAccountRepository accountRepository;
  private final GoogleAdsFullSyncService fullSyncService;
  private final StringRedisTemplate redisTemplate;

  @Value("${app.redis.key-prefix:messaging}")
  private String keyPrefix;

  @Scheduled(
      fixedDelayString = "${app.ads.google.background-sync-interval:30m}",
      initialDelayString = "${app.ads.google.background-sync-initial-delay:5m}")
  public void synchronizeManagedAccounts() {
    if (!properties.configured() || !properties.isBackgroundSyncEnabled()) return;

    var accounts =
        accountRepository.findByProviderAndManagedTrueAndAccessibleTrueOrderByIdAsc(
            AdProvider.GOOGLE_ADS);

    for (var account : accounts) {
      String lockKey =
          keyPrefix + ":ads:google:background-sync:" + account.getId();
      String token = UUID.randomUUID().toString();

      Boolean acquired =
          redisTemplate
              .opsForValue()
              .setIfAbsent(
                  lockKey, token, properties.getBackgroundSyncLockTtl());
      if (!Boolean.TRUE.equals(acquired)) continue;

      try {
        var result =
            fullSyncService.sync(account.getCompanyId(), account.getId());

        account.setLastSyncAt(Instant.now());
        accountRepository.save(account);

        if (result.failedOperations().isEmpty()) {
          log.info(
              "google_ads_background_sync_success companyId={} accountId={} campaigns={} adGroups={} ads={} analyticsRows={}",
              account.getCompanyId(),
              account.getId(),
              result.campaigns(),
              result.adGroups(),
              result.ads(),
              result.analyticsRows());
        } else {
          log.warn(
              "google_ads_background_sync_partial companyId={} accountId={} failedOperations={}",
              account.getCompanyId(),
              account.getId(),
              result.failedOperations());
        }
      } catch (RuntimeException exception) {
        log.warn(
            "google_ads_background_sync_failed companyId={} accountId={} errorType={} message={}",
            account.getCompanyId(),
            account.getId(),
            exception.getClass().getSimpleName(),
            safeMessage(exception));
      } finally {
        redisTemplate.execute(
            RELEASE_LOCK_SCRIPT, List.of(lockKey), token);
      }
    }
  }

  private String safeMessage(RuntimeException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) return "no-message";
    return message.length() <= 500 ? message : message.substring(0, 500);
  }
}
