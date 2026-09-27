package com.messaging.managedsms.control.repository;
import com.messaging.managedsms.control.entity.ManagedSmsTemplate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ManagedSmsTemplateRepository extends JpaRepository<ManagedSmsTemplate, Long> {
  Optional<ManagedSmsTemplate> findByIdAndCompanyId(Long id, Long companyId);
  List<ManagedSmsTemplate> findByCompanyIdAndArchivedAtIsNullOrderByUpdatedAtDesc(Long companyId);
}
