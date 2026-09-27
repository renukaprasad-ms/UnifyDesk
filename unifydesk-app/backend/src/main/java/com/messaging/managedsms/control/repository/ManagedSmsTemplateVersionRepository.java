package com.messaging.managedsms.control.repository;
import com.messaging.managedsms.control.entity.ManagedSmsTemplateVersion;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.TemplateVersionStatus;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ManagedSmsTemplateVersionRepository extends JpaRepository<ManagedSmsTemplateVersion, Long> {
  Optional<ManagedSmsTemplateVersion> findByIdAndCompanyId(Long id, Long companyId);
  List<ManagedSmsTemplateVersion> findByTemplateIdAndCompanyIdOrderByVersionNoDesc(Long templateId, Long companyId);
  Optional<ManagedSmsTemplateVersion> findFirstByTemplateIdAndCompanyIdOrderByVersionNoDesc(Long templateId, Long companyId);
  Optional<ManagedSmsTemplateVersion> findFirstByTemplateIdAndCompanyIdAndStatusOrderByVersionNoDesc(Long templateId, Long companyId, TemplateVersionStatus status);
  boolean existsByCompanyIdAndDltTemplateId(Long companyId, String dltTemplateId);
  long countByCompanyIdAndStatus(Long companyId, TemplateVersionStatus status);
  List<ManagedSmsTemplateVersion> findByStatusInOrderByCreatedAtAsc(Collection<TemplateVersionStatus> status);
}
