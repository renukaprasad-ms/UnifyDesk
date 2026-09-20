package com.messaging.channel.repository;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelConnectionRepository extends JpaRepository<ChannelConnection, Long> {

  List<ChannelConnection> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

  List<ChannelConnection> findByCompanyIdAndChannelTypeOrderByCreatedAtDesc(
      Long companyId, ChannelType channelType);

  Optional<ChannelConnection> findByIdAndCompanyId(Long id, Long companyId);

  Optional<ChannelConnection> findByCompanyIdAndProviderTypeAndExternalAccountId(
      Long companyId, ProviderType providerType, String externalAccountId);

  long countByCompanyIdAndChannelTypeAndStatusNot(
      Long companyId, ChannelType channelType, ChannelConnectionStatus status);

  boolean existsByCompanyIdAndProviderTypeAndExternalAccountId(
      Long companyId, ProviderType providerType, String externalAccountId);

  List<ChannelConnection> findByProviderTypeAndExternalAccountIdAndStatus(
      ProviderType providerType,
      String externalAccountId,
      ChannelConnectionStatus status);

  List<ChannelConnection> findByProviderTypeAndStatus(
      ProviderType providerType, ChannelConnectionStatus status);

}
