package com.messaging.ads.campaign.service;

import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.entity.ProviderDeployment;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.ProviderDeploymentStatus;
import com.messaging.ads.campaign.enums.ReachlyoCampaignStatus;
import com.messaging.ads.campaign.repository.CampaignChannelRepository;
import com.messaging.ads.campaign.repository.ProviderDeploymentRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProviderDeploymentService {

  private final ReachlyoCampaignRepository campaignRepository;
  private final CampaignChannelRepository channelRepository;
  private final ProviderDeploymentRepository deploymentRepository;
  private final AdAccountRepository accountRepository;
  private final AdCampaignRepository adCampaignRepository;

  @Transactional
  public ProviderDeployment createPending(
      Long companyId, Long reachlyoCampaignId, Long channelId, Long adAccountId) {
    ReachlyoCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(reachlyoCampaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Reachlyo campaign not found"));
    if (campaign.getStatus() == ReachlyoCampaignStatus.ARCHIVED) {
      throw new ConflictException("Archived campaigns cannot create provider deployments");
    }

    CampaignChannel channel =
        channelRepository
            .findByIdAndCompanyIdAndReachlyoCampaignId(channelId, companyId, reachlyoCampaignId)
            .orElseThrow(() -> new NotFoundException("Campaign channel not found"));
    if (!channel.isEnabled()) {
      throw new BadRequestException("Disabled campaign channels cannot be deployed");
    }

    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(adAccountId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising account not found"));
    if (!account.isAccessible() || !account.isManaged()) {
      throw new BadRequestException("Advertising account must be accessible and managed");
    }
    if (account.getProvider() != channel.getProvider()) {
      throw new BadRequestException("Advertising account provider does not match campaign channel");
    }

    if (deploymentRepository
        .findByCompanyIdAndCampaignChannelIdAndAdAccountId(companyId, channelId, adAccountId)
        .isPresent()) {
      throw new ConflictException("A provider deployment already exists for this channel and account");
    }

    ProviderDeployment deployment = new ProviderDeployment();
    deployment.setCompanyId(companyId);
    deployment.setReachlyoCampaignId(reachlyoCampaignId);
    deployment.setCampaignChannelId(channelId);
    deployment.setProvider(channel.getProvider());
    deployment.setAdAccountId(adAccountId);
    deployment.setDeploymentStatus(ProviderDeploymentStatus.PENDING);
    return deploymentRepository.save(deployment);
  }

  @Transactional(readOnly = true)
  public ProviderDeployment get(Long companyId, Long deploymentId) {
    return deploymentRepository
        .findByIdAndCompanyId(deploymentId, companyId)
        .orElseThrow(() -> new NotFoundException("Provider deployment not found"));
  }

  @Transactional
  public ProviderDeployment bindProviderCampaign(
      Long companyId, Long deploymentId, Long localCampaignId) {
    ProviderDeployment deployment =
        deploymentRepository
            .findByIdAndCompanyId(deploymentId, companyId)
            .orElseThrow(() -> new NotFoundException("Provider deployment not found"));
    AdCampaign adCampaign =
        adCampaignRepository
            .findByIdAndCompanyId(localCampaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));

    if (!deployment.getAdAccountId().equals(adCampaign.getAdAccountId())
        || deployment.getProvider() != adCampaign.getProvider()) {
      throw new BadRequestException(
          "Provider campaign does not match the deployment account/provider");
    }

    deployment.setLocalCampaignId(adCampaign.getId());
    deployment.setExternalCampaignId(adCampaign.getExternalCampaignId());
    deployment.setProviderStatus(adCampaign.getProviderStatus());
    deployment.setLastSyncAt(Instant.now());
    if (adCampaign.getStatus() == AdEntityStatus.ACTIVE) {
      deployment.setDeploymentStatus(ProviderDeploymentStatus.ACTIVE);
    } else if (adCampaign.getStatus() == AdEntityStatus.PAUSED) {
      deployment.setDeploymentStatus(ProviderDeploymentStatus.PAUSED);
    }
    return deploymentRepository.save(deployment);
  }

  @Transactional
  public ProviderDeployment markFailure(
      Long companyId, Long deploymentId, String errorCode, String errorMessage) {
    ProviderDeployment deployment =
        deploymentRepository
            .findByIdAndCompanyId(deploymentId, companyId)
            .orElseThrow(() -> new NotFoundException("Provider deployment not found"));
    deployment.setDeploymentStatus(ProviderDeploymentStatus.FAILED);
    deployment.setLastErrorCode(trimTo(errorCode, 160));
    deployment.setLastErrorMessage(trimTo(errorMessage, 2000));
    return deploymentRepository.save(deployment);
  }

  private String trimTo(String value, int length) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.length() <= length ? trimmed : trimmed.substring(0, length);
  }
}
