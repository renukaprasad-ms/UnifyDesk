package com.messaging.inbox.repository;

import com.messaging.inbox.entity.InboxParticipant;
import com.messaging.inbox.enums.InboxParticipantType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxParticipantRepository extends JpaRepository<InboxParticipant, Long> {

  Optional<InboxParticipant> findByConversationIdAndParticipantTypeAndAddressIgnoreCase(
      Long conversationId, InboxParticipantType participantType, String address);

  List<InboxParticipant> findByConversationIdOrderByAddressAsc(Long conversationId);
}
