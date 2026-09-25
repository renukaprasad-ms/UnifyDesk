package com.messaging.emailtemplate.service;

import com.messaging.common.exception.NotFoundException;
import com.messaging.emailtemplate.dto.EmailTemplateCatalogResponse;
import com.messaging.emailtemplate.entity.EmailTemplateCatalogItem;
import com.messaging.emailtemplate.enums.EmailTemplateCategory;
import com.messaging.emailtemplate.repository.EmailTemplateCatalogRepository;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailTemplateCatalogService {

  private final EmailTemplateCatalogRepository repository;
  private final EmailTemplateCompiler compiler;
  private final ConcurrentMap<Long, EmailTemplateCatalogResponse> cache = new ConcurrentHashMap<>();

  @Transactional(readOnly = true)
  public List<EmailTemplateCatalogResponse> list(EmailTemplateCategory category) {
    List<EmailTemplateCatalogItem> items =
        category == null
            ? repository.findByActiveTrueOrderBySortOrderAscNameAsc()
            : repository.findByActiveTrueAndCategoryOrderBySortOrderAscNameAsc(category);
    return items.stream().map(this::cachedResponse).toList();
  }

  @Transactional(readOnly = true)
  public EmailTemplateCatalogResponse get(Long id) {
    return cachedResponse(
        repository
            .findById(id)
            .filter(EmailTemplateCatalogItem::isActive)
            .orElseThrow(() -> new NotFoundException("Catalog template not found")));
  }

  private EmailTemplateCatalogResponse cachedResponse(EmailTemplateCatalogItem item) {
    return cache.computeIfAbsent(item.getId(), ignored -> response(item));
  }

  private EmailTemplateCatalogResponse response(EmailTemplateCatalogItem item) {
    var compiled =
        compiler.compile(
            item.getEditorMode(),
            item.getSubjectTemplate(),
            item.getPreheaderTemplate(),
            item.getDocumentJson(),
            item.getHtmlSource());
    return EmailTemplateCatalogResponse.from(item, compiled.html(), compiled.variables());
  }
}
