package com.messaging.broadcast.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.broadcast.entity.BroadcastOutbox;
import com.messaging.broadcast.event.BroadcastRunPrepareRequestedEvent;
import com.messaging.broadcast.repository.BroadcastOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BroadcastOutboxService {

  public static final String RUN_PREPARE_EVENT = "broadcast.run.prepare.requested";

  private final BroadcastOutboxRepository repository;
  private final ObjectMapper objectMapper;

  public void enqueueRunPrepare(Long companyId, Long runId) {
    enqueueRunPrepare(companyId, runId, 0L);
  }

  public void enqueueRunPrepare(Long companyId, Long runId, Long expectedAfterMemberId) {
    BroadcastOutbox event = new BroadcastOutbox();
    event.setCompanyId(companyId);
    event.setAggregateType("BROADCAST_RUN");
    event.setAggregateId(runId);
    event.setEventType(RUN_PREPARE_EVENT);
    try {
      event.setPayloadJson(
          objectMapper.writeValueAsString(
              new BroadcastRunPrepareRequestedEvent(
                  runId, expectedAfterMemberId == null ? 0L : expectedAfterMemberId)));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize broadcast outbox event", exception);
    }
    repository.save(event);
  }
}
