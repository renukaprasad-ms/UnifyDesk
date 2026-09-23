package com.messaging.emailtemplate.repository;

import com.messaging.emailtemplate.entity.EmailTemplateVersion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailTemplateVersionRepository extends JpaRepository<EmailTemplateVersion, Long> {
  Optional<EmailTemplateVersion> findByIdAndTemplateId(Long id, Long templateId);
  Optional<EmailTemplateVersion> findByTemplateIdAndVersionNumber(Long templateId, long versionNumber);
  List<EmailTemplateVersion> findAllByIdIn(Collection<Long> ids);
  List<EmailTemplateVersion> findByTemplateIdOrderByVersionNumberDesc(Long templateId);
}
