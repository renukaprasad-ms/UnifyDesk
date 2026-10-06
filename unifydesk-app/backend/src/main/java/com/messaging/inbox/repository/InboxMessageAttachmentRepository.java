package com.messaging.inbox.repository;

import com.messaging.inbox.entity.InboxMessageAttachment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxMessageAttachmentRepository
    extends JpaRepository<InboxMessageAttachment, Long> {

  Optional<InboxMessageAttachment> findByMessageIdAndExternalAttachmentId(
      Long messageId, String externalAttachmentId);

  Optional<InboxMessageAttachment> findByIdAndMessageConnectionCompanyId(
      Long id, Long companyId);

  List<InboxMessageAttachment> findByMessageIdOrderByFilenameAsc(Long messageId);

  List<InboxMessageAttachment> findByMessageIdInOrderByFilenameAsc(Collection<Long> messageIds);
}
