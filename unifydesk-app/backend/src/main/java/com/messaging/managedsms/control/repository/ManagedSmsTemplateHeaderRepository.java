package com.messaging.managedsms.control.repository;
import com.messaging.managedsms.control.entity.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ManagedSmsTemplateHeaderRepository extends JpaRepository<ManagedSmsTemplateHeader, ManagedSmsTemplateHeaderId> {
  List<ManagedSmsTemplateHeader> findByCompanyIdAndIdTemplateVersionId(Long companyId, Long templateVersionId);
}
