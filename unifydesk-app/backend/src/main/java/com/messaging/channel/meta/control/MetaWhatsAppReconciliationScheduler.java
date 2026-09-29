package com.messaging.channel.meta.control;

import com.messaging.channel.meta.MetaWhatsAppConnectionService;
import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.ProductState;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
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
            control -> {
              try {
                connectionService.reconcileDue(
                    control.getCompanyId(), control.getConnectionId());
              } catch (RuntimeException exception) {
                log.warn(
                    "meta_whatsapp_reconcile_failed companyId={} connectionId={} errorType={}",
                    control.getCompanyId(),
                    control.getConnectionId(),
                    exception.getClass().getSimpleName());
              }
            });
  }
}
