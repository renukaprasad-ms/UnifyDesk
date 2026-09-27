package com.messaging.managedsms.control.repository;
import com.messaging.managedsms.control.entity.ManagedSmsDltProfile;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.DltProfileStatus;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ManagedSmsDltProfileRepository extends JpaRepository<ManagedSmsDltProfile, Long> {
  Optional<ManagedSmsDltProfile> findByIdAndCompanyId(Long id, Long companyId);
  Optional<ManagedSmsDltProfile> findFirstByCompanyIdAndArchivedAtIsNullOrderByCreatedAtDesc(Long companyId);
  boolean existsByCompanyIdAndPeIdAndArchivedAtIsNull(Long companyId, String peId);
  List<ManagedSmsDltProfile> findByStatusInOrderByUpdatedAtAsc(Collection<DltProfileStatus> status);
}
