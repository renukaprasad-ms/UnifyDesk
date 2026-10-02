package com.messaging.broadcast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.messaging.broadcast.config.BroadcastProperties;
import com.messaging.broadcast.dto.BroadcastStatsResponse;
import com.messaging.broadcast.entity.BroadcastMessage;
import com.messaging.broadcast.entity.BroadcastRun;
import com.messaging.broadcast.enums.BroadcastMessageStatus;
import com.messaging.broadcast.enums.BroadcastRunStatus;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.repository.BroadcastBatchRepository;
import com.messaging.broadcast.repository.BroadcastGroupMemberRepository;
import com.messaging.broadcast.repository.BroadcastMessageRepository;
import com.messaging.broadcast.repository.BroadcastRecipientRepository;
import com.messaging.broadcast.repository.BroadcastRunRepository;
import com.messaging.broadcast.repository.BroadcastSuppressionRepository;
import com.messaging.channel.enums.ChannelType;
import com.messaging.contacts.audience.AudienceSnapshotRepository;
import com.messaging.contacts.audience.AudienceSnapshotService;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.Purpose;
import com.messaging.notification.platform.core.event.NotificationEventRecorder;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BroadcastTerminalNotificationServiceTests {

  @Test
  void zeroPendingPreparationCompletesAndRecordsDurableTerminalEvent() {
    Fixture fixture = fixture(0);

    fixture.preparation.process(30L, 0L);

    assertThat(fixture.run.getStatus()).isEqualTo(BroadcastRunStatus.COMPLETED);
    assertThat(fixture.run.isSnapshotComplete()).isTrue();
    assertThat(fixture.run.getStartedAt()).isNotNull();
    assertThat(fixture.run.getCompletedAt()).isNotNull();
    assertThat(fixture.message.getStatus()).isEqualTo(BroadcastMessageStatus.COMPLETED);
    verify(fixture.recorder)
        .recordBroadcastCompleted(
            eq(30L), eq(20L), eq(10L), eq("EMAIL"), eq(fixture.run.getCompletedAt()));
    verify(fixture.progress).publish(30L, true);
  }

  @Test
  void replayedZeroPendingPreparationDoesNotRecordSecondTerminalEvent() {
    Fixture fixture = fixture(0);

    fixture.preparation.process(30L, 0L);
    fixture.preparation.process(30L, 0L);

    verify(fixture.recorder, times(1))
        .recordBroadcastCompleted(
            eq(30L), eq(20L), eq(10L), eq("EMAIL"), any(Instant.class));
    verify(fixture.audienceSnapshotService, times(1)).get(10L, 90L);
  }

  @Test
  void nonZeroPendingPreparationTransitionsToRunningWithoutTerminalNotification() {
    Fixture fixture = fixture(1);

    fixture.preparation.process(30L, 0L);

    assertThat(fixture.run.getStatus()).isEqualTo(BroadcastRunStatus.RUNNING);
    assertThat(fixture.run.isSnapshotComplete()).isTrue();
    assertThat(fixture.message.getStatus()).isEqualTo(BroadcastMessageStatus.RUNNING);
    verifyNoInteractions(fixture.recorder);
    verify(fixture.progress).publish(30L, true);
  }

  @Test
  void ordinaryRunFinalizerStillRecordsCompletedNotification() {
    BroadcastRunRepository runs = mock(BroadcastRunRepository.class);
    BroadcastBatchRepository batches = mock(BroadcastBatchRepository.class);
    BroadcastMessageRepository messages = mock(BroadcastMessageRepository.class);
    BroadcastStatsService stats = mock(BroadcastStatsService.class);
    BroadcastProgressPublisher progress = mock(BroadcastProgressPublisher.class);
    NotificationEventRecorder recorder = mock(NotificationEventRecorder.class);
    BroadcastRunFinalizerService finalizer =
        new BroadcastRunFinalizerService(runs, batches, messages, stats, progress, recorder);

    BroadcastRun run = run(BroadcastRunStatus.RUNNING);
    run.setSnapshotComplete(true);
    BroadcastMessage message = message();

    when(runs.findByIdForUpdate(30L)).thenReturn(Optional.of(run));
    when(batches.countByRunIdAndStatusIn(eq(30L), any())).thenReturn(0L);
    when(stats.get(30L)).thenReturn(stats(0, 0, 0, 0));
    when(messages.findById(20L)).thenReturn(Optional.of(message));

    finalizer.tryFinalize(30L);

    assertThat(run.getStatus()).isEqualTo(BroadcastRunStatus.COMPLETED);
    assertThat(message.getStatus()).isEqualTo(BroadcastMessageStatus.COMPLETED);
    verify(recorder)
        .recordBroadcastCompleted(
            eq(30L), eq(20L), eq(10L), eq("EMAIL"), eq(run.getCompletedAt()));
    verify(progress).publish(30L, true);
  }

  @Test
  void ordinaryRunFailureStillRecordsFailedNotification() {
    BroadcastRunRepository runs = mock(BroadcastRunRepository.class);
    BroadcastMessageRepository messages = mock(BroadcastMessageRepository.class);
    BroadcastStatsService stats = mock(BroadcastStatsService.class);
    BroadcastProgressPublisher progress = mock(BroadcastProgressPublisher.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    NotificationEventRecorder recorder = mock(NotificationEventRecorder.class);
    BroadcastRunFailureService failure =
        new BroadcastRunFailureService(runs, messages, stats, progress, jdbc, recorder);

    BroadcastRun run = run(BroadcastRunStatus.RUNNING);
    BroadcastMessage message = message();

    when(runs.findByIdForUpdate(30L)).thenReturn(Optional.of(run));
    when(messages.findById(20L)).thenReturn(Optional.of(message));
    when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);

    failure.fail(30L, "TEST_FAILURE", "test failure");

    assertThat(run.getStatus()).isEqualTo(BroadcastRunStatus.FAILED);
    assertThat(message.getStatus()).isEqualTo(BroadcastMessageStatus.FAILED);
    verify(recorder)
        .recordBroadcastFailed(
            eq(30L), eq(20L), eq(10L), eq("EMAIL"), eq(run.getCompletedAt()));
    verify(progress).publish(30L, true);
  }

  private Fixture fixture(long pending) {
    BroadcastRunRepository runs = mock(BroadcastRunRepository.class);
    BroadcastMessageRepository messages = mock(BroadcastMessageRepository.class);
    BroadcastGroupMemberRepository members = mock(BroadcastGroupMemberRepository.class);
    BroadcastBatchRepository batches = mock(BroadcastBatchRepository.class);
    BroadcastRecipientRepository recipients = mock(BroadcastRecipientRepository.class);
    BroadcastSuppressionRepository suppressions = mock(BroadcastSuppressionRepository.class);
    BroadcastStatsService stats = mock(BroadcastStatsService.class);
    BroadcastOutboxService outbox = mock(BroadcastOutboxService.class);
    BroadcastProperties properties = new BroadcastProperties();
    BroadcastProgressPublisher progress = mock(BroadcastProgressPublisher.class);
    BroadcastRunFailureService failure = mock(BroadcastRunFailureService.class);
    AudienceSnapshotService audienceSnapshots = mock(AudienceSnapshotService.class);
    NotificationEventRecorder recorder = mock(NotificationEventRecorder.class);

    BroadcastPreparationChunkService preparation =
        new BroadcastPreparationChunkService(
            runs,
            messages,
            members,
            batches,
            recipients,
            suppressions,
            stats,
            outbox,
            properties,
            progress,
            failure,
            audienceSnapshots,
            recorder);

    BroadcastRun run = run(BroadcastRunStatus.PREPARING);
    run.setAudienceSnapshotId(90L);
    BroadcastMessage message = message();
    AudienceSnapshotRepository.Snapshot snapshot =
        new AudienceSnapshotRepository.Snapshot(
            90L,
            10L,
            "LIST",
            80L,
            "1",
            null,
            Channel.EMAIL,
            Purpose.MARKETING,
            "READY",
            5L,
            5L,
            5L,
            null,
            Instant.parse("2026-10-02T06:00:00Z"),
            Instant.parse("2026-10-02T06:01:00Z"));

    when(runs.findByIdForUpdate(30L)).thenReturn(Optional.of(run));
    when(audienceSnapshots.get(10L, 90L)).thenReturn(snapshot);
    when(audienceSnapshots.pageMembers(10L, 90L, 0L, properties.getSnapshotBatchSize()))
        .thenReturn(List.of());
    when(stats.get(30L)).thenReturn(stats(pending, 0, 0, 5));
    when(messages.findById(20L)).thenReturn(Optional.of(message));

    return new Fixture(preparation, run, message, audienceSnapshots, progress, recorder);
  }

  private BroadcastRun run(BroadcastRunStatus status) {
    BroadcastRun run = new BroadcastRun();
    run.setId(30L);
    run.setBroadcastMessageId(20L);
    run.setCompanyId(10L);
    run.setProviderType(BroadcastSenderProvider.AMAZON_SES);
    run.setStatus(status);
    run.setBodyTextSnapshot("body");
    run.setCreatedAt(Instant.parse("2026-10-02T06:00:00Z"));
    return run;
  }

  private BroadcastMessage message() {
    BroadcastMessage message = new BroadcastMessage();
    message.setId(20L);
    message.setCompanyId(10L);
    message.setChannelType(ChannelType.EMAIL);
    message.setBodyText("body");
    message.setStatus(BroadcastMessageStatus.RUNNING);
    message.setCreatedByUserId(7L);
    return message;
  }

  private BroadcastStatsResponse stats(long pending, long failed, long bounced, long skipped) {
    return new BroadcastStatsResponse(
        pending + failed + bounced + skipped,
        pending,
        0,
        0,
        failed,
        bounced,
        0,
        skipped);
  }

  private record Fixture(
      BroadcastPreparationChunkService preparation,
      BroadcastRun run,
      BroadcastMessage message,
      AudienceSnapshotService audienceSnapshotService,
      BroadcastProgressPublisher progress,
      NotificationEventRecorder recorder) {}
}
