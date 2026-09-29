package com.messaging.channel.meta.control;

import com.messaging.channel.meta.control.MetaWhatsAppConnectionControl.ProductState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetaWhatsAppConnectionControlRepository
    extends JpaRepository<MetaWhatsAppConnectionControl, Long> {

  Optional<MetaWhatsAppConnectionControl> findByConnectionIdAndCompanyId(
      Long connectionId, Long companyId);

  List<MetaWhatsAppConnectionControl> findByCompanyIdOrderByUpdatedAtDesc(Long companyId);

  Optional<MetaWhatsAppConnectionControl> findFirstBySelectedPhoneNumberIdAndProductStateNot(
      String selectedPhoneNumberId, ProductState excludedState);

  List<MetaWhatsAppConnectionControl>
      findTop25ByNextReconcileAtLessThanEqualAndProductStateNotOrderByNextReconcileAtAsc(
          Instant now, ProductState excludedState);
}
