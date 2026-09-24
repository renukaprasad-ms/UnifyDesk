package com.messaging.ads.campaign.service;

import com.messaging.ads.campaign.dto.ConversionGoalMappingResponse;
import com.messaging.ads.campaign.dto.UpsertConversionGoalMappingRequest;
import com.messaging.ads.campaign.entity.ConversionGoalMapping;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.ConversionTargetType;
import com.messaging.ads.campaign.enums.ReachlyoCampaignStatus;
import com.messaging.ads.campaign.repository.ConversionGoalMappingRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdConversion;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdConversionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversionGoalMappingService {

  private final ReachlyoCampaignRepository campaignRepository;
  private final ConversionGoalMappingRepository mappingRepository;
  private final AdAccountRepository accountRepository;
  private final AdConversionRepository conversionRepository;

  @Transactional(readOnly = true)
  public List<ConversionGoalMappingResponse> list(Long companyId, Long campaignId) {
    requireCampaign(companyId, campaignId);
    return mappingRepository
        .findByCompanyIdAndReachlyoCampaignIdOrderByProviderAsc(companyId, campaignId)
        .stream()
        .map(ConversionGoalMappingResponse::from)
        .toList();
  }

  @Transactional
  public ConversionGoalMappingResponse upsert(
      Long companyId, Long campaignId, UpsertConversionGoalMappingRequest request) {
    ReachlyoCampaign campaign = requireCampaign(companyId, campaignId);
    requireEditable(campaign);

    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(request.adAccountId(), companyId)
            .orElseThrow(() -> new NotFoundException("Advertising account not found"));
    if (!account.isAccessible() || !account.isManaged()) {
      throw new BadRequestException("Advertising account must be accessible and managed");
    }
    if (account.getProvider() != request.provider()) {
      throw new BadRequestException("Advertising account provider does not match conversion mapping");
    }

    ConversionGoalMapping mapping =
        mappingRepository
            .findByCompanyIdAndReachlyoCampaignIdAndProviderAndAdAccountIdAndBusinessGoal(
                companyId,
                campaignId,
                request.provider(),
                request.adAccountId(),
                campaign.getBusinessGoal())
            .orElseGet(ConversionGoalMapping::new);

    if (mapping.getId() == null) {
      mapping.setCompanyId(companyId);
      mapping.setReachlyoCampaignId(campaignId);
      mapping.setBusinessGoal(campaign.getBusinessGoal());
      mapping.setProvider(request.provider());
      mapping.setAdAccountId(request.adAccountId());
    }

    mapping.setTargetType(request.targetType());
    if (request.targetType() == ConversionTargetType.PROVIDER_CONVERSION) {
      if (request.adConversionId() == null) {
        throw new BadRequestException("Provider conversion target requires adConversionId");
      }
      AdConversion conversion =
          conversionRepository
              .findByIdAndCompanyId(request.adConversionId(), companyId)
              .orElseThrow(() -> new NotFoundException("Advertising conversion not found"));
      if (!conversion.getAdAccountId().equals(account.getId())
          || conversion.getProvider() != request.provider()) {
        throw new BadRequestException("Advertising conversion does not match selected account/provider");
      }
      if (!conversion.isAccessible() || conversion.isArchived() || conversion.isUnavailable()) {
        throw new BadRequestException("Advertising conversion is not available for campaign use");
      }
      mapping.setAdConversionId(conversion.getId());
      mapping.setExternalConversionId(conversion.getExternalConversionId());
      mapping.setProviderEventName(null);
    } else {
      String eventName = normalizeText(request.providerEventName());
      if (eventName == null) {
        throw new BadRequestException("Standard event target requires providerEventName");
      }
      mapping.setAdConversionId(null);
      mapping.setExternalConversionId(null);
      mapping.setProviderEventName(eventName);
    }

    mapping.setValidated(true);
    mappingRepository.save(mapping);
    return ConversionGoalMappingResponse.from(mapping);
  }

  @Transactional
  public void delete(Long companyId, Long campaignId, Long mappingId) {
    ReachlyoCampaign campaign = requireCampaign(companyId, campaignId);
    requireEditable(campaign);
    ConversionGoalMapping mapping =
        mappingRepository
            .findByIdAndCompanyIdAndReachlyoCampaignId(mappingId, companyId, campaignId)
            .orElseThrow(() -> new NotFoundException("Conversion goal mapping not found"));
    mappingRepository.delete(mapping);
  }

  private ReachlyoCampaign requireCampaign(Long companyId, Long campaignId) {
    return campaignRepository
        .findByIdAndCompanyId(campaignId, companyId)
        .orElseThrow(() -> new NotFoundException("Reachlyo campaign not found"));
  }

  private void requireEditable(ReachlyoCampaign campaign) {
    if (campaign.getStatus() != ReachlyoCampaignStatus.DRAFT
        && campaign.getStatus() != ReachlyoCampaignStatus.READY) {
      throw new ConflictException("Conversion mapping can only be edited while campaign is DRAFT or READY");
    }
  }

  private String normalizeText(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
