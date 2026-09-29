package com.messaging.channel.meta.control;

import com.messaging.channel.meta.MetaWhatsAppConnectionService;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.ProductState;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetaWhatsAppReconciliationScheduler {

  private final MetaWhatsAppConnectionControlRepository controlRepository;
  private final MetaWhatsAppConnectionService connectionService;

  @Scheduled(
      fixedDelayString = "#{@metaWhatsAppProperties.reconcilePollInterval.toMillis()}",
      initialDelayString = "60000")
  public void reconcileDueConnections() {
    controlRepository
        .findTop25ByNextReconcileAtLessThanEqualAndProductStateNotOrderByNextReconcileAtAsc(
            Instant.now(), ProductState.DISCONNECTED)
        .forEach(
            control ->
                connectionService.reconcileDue(
                    control.getCompanyId(), control.getConnectionId()));
  }
}
