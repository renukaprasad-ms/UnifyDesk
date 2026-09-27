package com.messaging.notification.platform.delivery.policy;

import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DigestBucket;
import com.messaging.notification.platform.delivery.model.DeliveryModels.QuietHours;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationDeliveryPolicy {

  private static final LocalTime DEFAULT_QUIET_START = LocalTime.of(22, 0);
  private static final LocalTime DEFAULT_QUIET_END = LocalTime.of(8, 0);

  private final NotificationDeliveryProperties properties;

  public Instant digestEligibleAt(
      Instant firstContributionAt, Instant lastContributionAt, int contributionCount) {
    Instant maxWaitAt = firstContributionAt.plus(properties.getDigest().getMaxWait());
    if (contributionCount < properties.getDigest().getTriggerCount()) {
      return maxWaitAt;
    }
    Instant debounceAt = lastContributionAt.plus(properties.getDigest().getDebounce());
    return debounceAt.isBefore(maxWaitAt) ? debounceAt : maxWaitAt;
  }

  public Instant recomputeDigestEligibleAt(DigestBucket bucket, Instant latestContribution) {
    return digestEligibleAt(
        bucket.firstContributionAt(), latestContribution, bucket.contributionCount() + 1);
  }

  public Instant quietHoursDeferral(Instant now, QuietHours settings) {
    QuietHours effective = normalizeQuietHours(settings);
    if (!effective.enabled() || effective.start().equals(effective.end())) {
      return null;
    }

    ZoneId zone = safeZone(effective.timezone());
    ZonedDateTime localNow = now.atZone(zone);
    LocalTime time = localNow.toLocalTime();
    LocalTime start = effective.start();
    LocalTime end = effective.end();

    boolean wrapsMidnight = start.isAfter(end);
    boolean quiet =
        wrapsMidnight
            ? !time.isBefore(start) || time.isBefore(end)
            : !time.isBefore(start) && time.isBefore(end);

    if (!quiet) {
      return null;
    }

    LocalDate endDate;
    if (wrapsMidnight && !time.isBefore(start)) {
      endDate = localNow.toLocalDate().plusDays(1);
    } else {
      endDate = localNow.toLocalDate();
    }
    return resolveLocal(endDate, end, zone).toInstant();
  }

  public Instant emailRetryAt(Instant now, int attemptCount) {
    Duration[] schedule = {
      Duration.ofMinutes(1),
      Duration.ofMinutes(5),
      Duration.ofMinutes(15),
      Duration.ofHours(1),
      Duration.ofHours(4)
    };
    int index = Math.max(0, Math.min(attemptCount - 1, schedule.length - 1));
    return now.plus(schedule[index]);
  }

  public Instant pushRetryAt(Instant now, int attemptCount) {
    Duration[] schedule = {
      Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1)
    };
    int index = Math.max(0, Math.min(attemptCount - 1, schedule.length - 1));
    return now.plus(schedule[index]);
  }

  public boolean emailAttemptsExhausted(int attemptCount) {
    return attemptCount >= properties.getDelivery().getEmailMaxAttempts();
  }

  public boolean pushAttemptsExhausted(int attemptCount) {
    return attemptCount >= properties.getPush().getMaxAttempts();
  }

  private QuietHours normalizeQuietHours(QuietHours settings) {
    if (settings == null) {
      return new QuietHours("UTC", true, DEFAULT_QUIET_START, DEFAULT_QUIET_END);
    }
    String timezone =
        settings.timezone() == null || settings.timezone().isBlank() ? "UTC" : settings.timezone();
    LocalTime start = settings.start() == null ? DEFAULT_QUIET_START : settings.start();
    LocalTime end = settings.end() == null ? DEFAULT_QUIET_END : settings.end();
    return new QuietHours(timezone, settings.enabled(), start, end);
  }

  private ZoneId safeZone(String timezone) {
    try {
      return ZoneId.of(timezone);
    } catch (DateTimeException exception) {
      return ZoneId.of("UTC");
    }
  }

  private ZonedDateTime resolveLocal(LocalDate date, LocalTime time, ZoneId zone) {
    LocalDateTime localDateTime = LocalDateTime.of(date, time);
    return localDateTime.atZone(zone);
  }
}
