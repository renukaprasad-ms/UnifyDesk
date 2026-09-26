package com.messaging.managedemail.events;

import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedEmailEventReceiptService {

  private final ManagedEmailEventLedger ledger;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void receiveMatched(
      ManagedEmailProviderEvent event,
      ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime) {
    ledger.ensureMatchedReceipt(event, runtime);
  }
}
