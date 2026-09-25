package com.messaging.emailtemplate.repository;

import com.messaging.emailtemplate.entity.EmailTemplateAsset;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailTemplateAssetRepository extends JpaRepository<EmailTemplateAsset, Long> {
  Optional<EmailTemplateAsset> findByCompanyIdAndMediaId(Long companyId, Long mediaId);
  Optional<EmailTemplateAsset> findByPublicToken(String publicToken);
}
