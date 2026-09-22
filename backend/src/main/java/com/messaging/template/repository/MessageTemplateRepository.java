package com.messaging.template.repository;

import com.messaging.channel.enums.ChannelType;
import com.messaging.template.entity.MessageTemplate;
import com.messaging.template.enums.MessageTemplateStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {

  List<MessageTemplate> findByCompanyIdOrderByUpdatedAtDesc(Long companyId);

  List<MessageTemplate> findByCompanyIdAndChannelTypeOrderByUpdatedAtDesc(
      Long companyId, ChannelType channelType);

  List<MessageTemplate> findByCompanyIdAndStatusNotOrderByUpdatedAtDesc(
      Long companyId, MessageTemplateStatus status);

  List<MessageTemplate> findByCompanyIdAndChannelTypeAndStatusNotOrderByUpdatedAtDesc(
      Long companyId, ChannelType channelType, MessageTemplateStatus status);

  List<MessageTemplate> findByCompanyIdAndChannelTypeAndStatusInOrderByUpdatedAtDesc(
      Long companyId, ChannelType channelType, Collection<MessageTemplateStatus> statuses);

  Optional<MessageTemplate> findByIdAndCompanyId(Long id, Long companyId);

  boolean existsByCompanyIdAndChannelTypeAndNameIgnoreCaseAndStatusNot(
      Long companyId,
      ChannelType channelType,
      String name,
      MessageTemplateStatus status);

  boolean existsByCompanyIdAndChannelTypeAndNameIgnoreCaseAndStatusNotAndIdNot(
      Long companyId,
      ChannelType channelType,
      String name,
      MessageTemplateStatus status,
      Long id);
}
