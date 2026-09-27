package com.messaging.managedsms.control.repository;

import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.managedsms.control.entity.ManagedSmsProviderBinding;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.ProviderBindingStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedSmsProviderBindingRepository
    extends JpaRepository<ManagedSmsProviderBinding, Long> {
  Optional<ManagedSmsProviderBinding> findByIdAndCompanyId(Long id, Long companyId);

  Optional<ManagedSmsProviderBinding>
      findByCompanyIdAndTemplateVersionIdAndHeaderIdAndProvider(
          Long companyId,
          Long templateVersionId,
          Long headerId,
          BroadcastSenderProvider provider);

  List<ManagedSmsProviderBinding> findByCompanyIdAndTemplateVersionId(
      Long companyId, Long templateVersionId);

  List<ManagedSmsProviderBinding> findByCompanyId(Long companyId);

  long countByCompanyIdAndTemplateVersionIdAndStatus(
      Long companyId, Long templateVersionId, ProviderBindingStatus status);
}
