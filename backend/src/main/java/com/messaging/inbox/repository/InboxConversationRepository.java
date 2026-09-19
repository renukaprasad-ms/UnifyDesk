package com.messaging.inbox.repository;

import com.messaging.inbox.entity.InboxConversation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxConversationRepository extends JpaRepository<InboxConversation, Long> {

  Optional<InboxConversation> findByConnectionIdAndExternalConversationId(
      Long connectionId, String externalConversationId);

  List<InboxConversation> findByConnectionIdAndExternalConversationIdIn(
      Long connectionId, Collection<String> externalConversationIds);

  Optional<InboxConversation> findByIdAndCompanyId(Long id, Long companyId);

  Page<InboxConversation> findByCompanyIdOrderByLastMessageAtDescCreatedAtDesc(
      Long companyId, Pageable pageable);

  Page<InboxConversation> findByCompanyIdAndConnectionIdOrderByLastMessageAtDescCreatedAtDesc(
      Long companyId, Long connectionId, Pageable pageable);
}
