package com.messaging.emailtemplate.repository;

import com.messaging.emailtemplate.entity.EmailTemplate;
import com.messaging.emailtemplate.enums.EmailTemplateCategory;
import com.messaging.emailtemplate.enums.EmailTemplateStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

  @Query("""
      select t from EmailTemplate t
      where t.companyId = :companyId
        and (:includeArchived = true or t.status <> com.messaging.emailtemplate.enums.EmailTemplateStatus.ARCHIVED)
        and (:category is null or t.category = :category)
        and (:status is null or t.status = :status)
        and (:q is null or lower(t.name) like lower(concat('%', :q, '%')))
      order by t.updatedAt desc
      """)
  Page<EmailTemplate> search(
      @Param("companyId") Long companyId,
      @Param("q") String q,
      @Param("category") EmailTemplateCategory category,
      @Param("status") EmailTemplateStatus status,
      @Param("includeArchived") boolean includeArchived,
      Pageable pageable);

  Optional<EmailTemplate> findByIdAndCompanyId(Long id, Long companyId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from EmailTemplate t where t.id = :id and t.companyId = :companyId")
  Optional<EmailTemplate> findForUpdate(@Param("id") Long id, @Param("companyId") Long companyId);

  boolean existsByCompanyIdAndNameIgnoreCaseAndStatusNot(
      Long companyId, String name, EmailTemplateStatus status);

  boolean existsByCompanyIdAndNameIgnoreCaseAndStatusNotAndIdNot(
      Long companyId, String name, EmailTemplateStatus status, Long id);

  List<EmailTemplate> findByCompanyIdAndStatusOrderByUpdatedAtDesc(
      Long companyId, EmailTemplateStatus status);
}
