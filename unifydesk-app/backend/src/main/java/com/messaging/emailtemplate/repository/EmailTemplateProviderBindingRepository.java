package com.messaging.emailtemplate.repository;

import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.emailtemplate.entity.EmailTemplateProviderBinding;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailTemplateProviderBindingRepository
    extends JpaRepository<EmailTemplateProviderBinding, Long> {

  Optional<EmailTemplateProviderBinding>
      findByTemplateVersionIdAndConnectionIdAndProvider(
          Long templateVersionId, Long connectionId, BroadcastSenderProvider provider);

  Optional<EmailTemplateProviderBinding>
      findByIdAndCompanyId(Long id, Long companyId);

  List<EmailTemplateProviderBinding>
      findByCompanyIdAndTemplateIdOrderByUpdatedAtDesc(Long companyId, Long templateId);

  List<EmailTemplateProviderBinding>
      findByCompanyIdAndConnectionIdAndProviderOrderByUpdatedAtDesc(
          Long companyId, Long connectionId, BroadcastSenderProvider provider);
}
