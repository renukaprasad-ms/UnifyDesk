package com.messaging.managedemail.control.repository;

import com.messaging.managedemail.control.entity.ManagedEmailSenderBinding;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedEmailSenderBindingRepository
    extends JpaRepository<ManagedEmailSenderBinding, Long> {

  Optional<ManagedEmailSenderBinding> findBySenderIdAndCompanyId(Long senderId, Long companyId);

  List<ManagedEmailSenderBinding> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

  List<ManagedEmailSenderBinding> findByCompanyIdAndDomainId(Long companyId, Long domainId);

  long countByCompanyId(Long companyId);
}
