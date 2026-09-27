package com.messaging.managedsms.control.repository;
import com.messaging.managedsms.control.entity.ManagedSmsHeader;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ManagedSmsHeaderRepository extends JpaRepository<ManagedSmsHeader, Long> {
  Optional<ManagedSmsHeader> findByIdAndCompanyId(Long id, Long companyId);
  List<ManagedSmsHeader> findByCompanyIdAndArchivedAtIsNullOrderByCreatedAtDesc(Long companyId);
  boolean existsByCompanyIdAndHeaderIgnoreCaseAndArchivedAtIsNull(Long companyId, String header);
  long countByCompanyIdAndStatusAndProviderMappingStatusAndArchivedAtIsNull(Long companyId, HeaderStatus status, ProviderMappingStatus providerMappingStatus);
  List<ManagedSmsHeader> findByStatusInOrderByUpdatedAtAsc(Collection<HeaderStatus> status);
}
