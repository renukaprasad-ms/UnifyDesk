package com.messaging.inbox.repository;

import com.messaging.inbox.entity.InboxMessageAttachment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxMessageAttachmentRepository
    extends JpaRepository<InboxMessageAttachment, Long> {

  Optional<InboxMessageAttachment> findByMessageIdAndExternalAttachmentId(
      Long messageId, String externalAttachmentId);

  List<InboxMessageAttachment> findByMessageIdOrderByFilenameAsc(Long messageId);
}
