package com.messaging.broadcast.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.messaging.broadcast.enums.BroadcastMessageStatus;
import com.messaging.broadcast.enums.BroadcastRunStatus;
import com.messaging.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class BroadcastStateMachineTests {

  private final BroadcastStateMachine stateMachine = new BroadcastStateMachine();

  @Test
  void onlyDraftAndScheduledMessagesAreEditableAndSendable() {
    assertThatCode(() -> stateMachine.requireEditable(BroadcastMessageStatus.DRAFT))
        .doesNotThrowAnyException();
    assertThatCode(() -> stateMachine.requireEditable(BroadcastMessageStatus.SCHEDULED))
        .doesNotThrowAnyException();
    assertThatCode(() -> stateMachine.requireSendable(BroadcastMessageStatus.DRAFT))
        .doesNotThrowAnyException();
    assertThatCode(() -> stateMachine.requireSendable(BroadcastMessageStatus.SCHEDULED))
        .doesNotThrowAnyException();

    assertThatThrownBy(() -> stateMachine.requireEditable(BroadcastMessageStatus.RUNNING))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> stateMachine.requireSendable(BroadcastMessageStatus.COMPLETED))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void pauseResumeAndCancelFollowRunStateMachine() {
    assertThatCode(() -> stateMachine.requirePausable(BroadcastRunStatus.RUNNING))
        .doesNotThrowAnyException();
    assertThatCode(() -> stateMachine.requireResumable(BroadcastRunStatus.PAUSED))
        .doesNotThrowAnyException();
    assertThatCode(() -> stateMachine.requireCancellable(BroadcastRunStatus.PREPARING))
        .doesNotThrowAnyException();

    assertThatThrownBy(() -> stateMachine.requirePausable(BroadcastRunStatus.PAUSED))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> stateMachine.requireResumable(BroadcastRunStatus.RUNNING))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> stateMachine.requireCancellable(BroadcastRunStatus.COMPLETED))
        .isInstanceOf(BadRequestException.class);
  }
}
