package com.messaging.managedsms.control.repository;
import com.messaging.managedsms.control.entity.ManagedSmsSenderBinding;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ManagedSmsSenderBindingRepository extends JpaRepository<ManagedSmsSenderBinding, Long> {
  Optional<ManagedSmsSenderBinding> findBySenderIdAndCompanyId(Long senderId, Long companyId);
  Optional<ManagedSmsSenderBinding> findByCompanyIdAndHeaderId(Long companyId, Long headerId);
  List<ManagedSmsSenderBinding> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
  List<ManagedSmsSenderBinding> findByCompanyIdAndHeaderIdIn(Long companyId, Collection<Long> headerIds);
}
