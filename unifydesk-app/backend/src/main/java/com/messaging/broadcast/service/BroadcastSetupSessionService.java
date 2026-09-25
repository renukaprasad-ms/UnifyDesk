package com.messaging.broadcast.service;

import com.messaging.broadcast.dto.BroadcastSetupSessionResponse;
import com.messaging.broadcast.dto.UpsertBroadcastSetupSessionRequest;
import com.messaging.broadcast.entity.BroadcastSetupSession;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.enums.BroadcastSetupStatus;
import com.messaging.broadcast.repository.BroadcastSetupSessionRepository;
import com.messaging.channel.enums.ChannelType;
import com.messaging.common.exception.BadRequestException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BroadcastSetupSessionService {

  private static final EnumSet<BroadcastSetupStatus> ACTIVE_STATUSES =
      EnumSet.of(
          BroadcastSetupStatus.IN_PROGRESS,
          BroadcastSetupStatus.WAITING_EXTERNAL_ACTION,
          BroadcastSetupStatus.READY_TO_CONNECT);

  private final BroadcastSetupSessionRepository repository;

  @Transactional(readOnly = true)
  public Optional<BroadcastSetupSessionResponse> active(Long companyId, ChannelType channelType) {
    return repository
        .findFirstByCompanyIdAndChannelTypeAndStatusInOrderByUpdatedAtDesc(
            companyId, channelType, ACTIVE_STATUSES)
        .map(BroadcastSetupSessionResponse::from);
  }

  @Transactional
  public BroadcastSetupSessionResponse upsert(
      Long companyId, Long userId, UpsertBroadcastSetupSessionRequest request) {
    if (!request.provider().isBroadcastProvider()
        || !request.provider().supports(request.channelType())) {
      throw new BadRequestException(
          request.provider() + " is not a Broadcast provider for " + request.channelType());
    }

    BroadcastSetupSession session =
        repository
            .findFirstByCompanyIdAndChannelTypeAndStatusInOrderByUpdatedAtDesc(
                companyId, request.channelType(), ACTIVE_STATUSES)
            .orElse(null);

    if (session != null && session.getProvider() != request.provider()) {
      session.setStatus(BroadcastSetupStatus.CANCELLED);
      repository.save(session);
      session = null;
    }

    if (session == null) {
      session = new BroadcastSetupSession();
      session.setCompanyId(companyId);
      session.setChannelType(request.channelType());
      session.setProvider(request.provider());
      session.setCreatedByUserId(userId);
    }

    session.setCurrentStep(request.currentStep());
    session.setStatus(request.status());
    session.setSetupDataJson(trimToNull(request.setupDataJson()));
    if (request.status() == BroadcastSetupStatus.COMPLETED) {
      session.setCompletedAt(Instant.now());
    }

    return BroadcastSetupSessionResponse.from(repository.save(session));
  }

  @Transactional
  public void completeActive(Long companyId, BroadcastSenderProvider provider) {
    repository
        .findFirstByCompanyIdAndChannelTypeAndStatusInOrderByUpdatedAtDesc(
            companyId, provider.channelType(), ACTIVE_STATUSES)
        .filter(session -> session.getProvider() == provider)
        .ifPresent(
            session -> {
              session.setStatus(BroadcastSetupStatus.COMPLETED);
              session.setCompletedAt(Instant.now());
              repository.save(session);
            });
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
