package com.messaging.broadcast.service;

import com.messaging.broadcast.enums.BroadcastMessageStatus;
import com.messaging.broadcast.enums.BroadcastRunStatus;
import com.messaging.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

@Component
public class BroadcastStateMachine {

  public void requireEditable(BroadcastMessageStatus status) {
    if (status != BroadcastMessageStatus.DRAFT && status != BroadcastMessageStatus.SCHEDULED) {
      throw new BadRequestException("Only draft or scheduled broadcasts can be edited");
    }
  }

  public void requireSendable(BroadcastMessageStatus status) {
    if (status != BroadcastMessageStatus.DRAFT && status != BroadcastMessageStatus.SCHEDULED) {
      throw new BadRequestException("Broadcast has already been started");
    }
  }

  public void requirePausable(BroadcastRunStatus status) {
    if (status != BroadcastRunStatus.RUNNING) {
      throw new BadRequestException("Only a running broadcast can be paused");
    }
  }

  public void requireResumable(BroadcastRunStatus status) {
    if (status != BroadcastRunStatus.PAUSED) {
      throw new BadRequestException("Only a paused broadcast can be resumed");
    }
  }

  public void requireCancellable(BroadcastRunStatus status) {
    if (status == BroadcastRunStatus.COMPLETED
        || status == BroadcastRunStatus.PARTIALLY_FAILED
        || status == BroadcastRunStatus.CANCELLED
        || status == BroadcastRunStatus.FAILED) {
      throw new BadRequestException("Broadcast is already terminal");
    }
  }
}
