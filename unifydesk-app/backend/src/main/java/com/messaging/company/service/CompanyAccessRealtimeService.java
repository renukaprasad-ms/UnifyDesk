package com.messaging.company.service;

import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.realtime.service.RealtimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class CompanyAccessRealtimeService {

  public static final String EVENT_TYPE = "company.access.changed";

  private final RealtimeService realtimeService;

  public void notifyAfterCommit(
      Long userId, Long companyId, String reason, CompanyMemberStatus membershipStatus) {
    Runnable publish =
        () ->
            realtimeService.sendToUserId(
                userId,
                EVENT_TYPE,
                new AccessChangedPayload(
                    companyId.toString(),
                    reason,
                    membershipStatus == null ? null : membershipStatus.name()));

    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      publish.run();
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            publish.run();
          }
        });
  }

  public record AccessChangedPayload(
      String companyId, String reason, String membershipStatus) {}
}
