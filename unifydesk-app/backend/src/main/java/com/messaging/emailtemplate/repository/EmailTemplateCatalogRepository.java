package com.messaging.emailtemplate.repository;

import com.messaging.emailtemplate.entity.EmailTemplateCatalogItem;
import com.messaging.emailtemplate.enums.EmailTemplateCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailTemplateCatalogRepository extends JpaRepository<EmailTemplateCatalogItem, Long> {
  List<EmailTemplateCatalogItem> findByActiveTrueOrderBySortOrderAscNameAsc();
  List<EmailTemplateCatalogItem> findByActiveTrueAndCategoryOrderBySortOrderAscNameAsc(
      EmailTemplateCategory category);
}
