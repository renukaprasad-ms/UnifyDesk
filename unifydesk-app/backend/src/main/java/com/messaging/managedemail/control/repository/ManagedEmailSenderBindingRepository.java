package com.messaging.managedemail.control.repository;

import com.messaging.managedemail.control.entity.ManagedEmailSenderBinding;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagedEmailSenderBindingRepository
    extends JpaRepository<ManagedEmailSenderBinding, Long> {

  Optional<ManagedEmailSenderBinding> findBySenderIdAndCompanyId(Long senderId, Long companyId);

  List<ManagedEmailSenderBinding> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

  List<ManagedEmailSenderBinding> findByCompanyIdAndDomainId(Long companyId, Long domainId);

  @Query(
      """
      select count(binding)
      from ManagedEmailSenderBinding binding, BroadcastSender sender
      where binding.companyId = :companyId
        and sender.id = binding.senderId
        and sender.status = com.messaging.broadcast.enums.BroadcastSenderStatus.ACTIVE
      """)
  long countActiveByCompanyId(@Param("companyId") Long companyId);
}
