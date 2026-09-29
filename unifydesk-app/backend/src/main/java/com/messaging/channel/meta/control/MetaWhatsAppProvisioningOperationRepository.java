package com.messaging.channel.meta.control;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetaWhatsAppProvisioningOperationRepository
    extends JpaRepository<MetaWhatsAppProvisioningOperation, Long> {

  Optional<MetaWhatsAppProvisioningOperation> findByOperationKey(String operationKey);

  Optional<MetaWhatsAppProvisioningOperation> findFirstByConnectionIdOrderByUpdatedAtDesc(
      Long connectionId);
}
