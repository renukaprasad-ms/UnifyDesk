package com.messaging.ads.meta;

import com.messaging.ads.dto.AdCampaignResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.CreateMetaCampaignRequest;
import com.messaging.ads.meta.dto.UpdateMetaCampaignRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCampaignRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaAdsCampaignMutationService {

  private static final Set<String> SUPPORTED_OBJECTIVES =
      Set.of(
          "OUTCOME_AWARENESS",
          "OUTCOME_TRAFFIC",
          "OUTCOME_ENGAGEMENT",
          "OUTCOME_LEADS",
          "OUTCOME_APP_PROMOTION",
          "OUTCOME_SALES");

  private final AdAccountRepository accountRepository;
  private final AdCampaignRepository campaignRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;
  private final MetaAdsCampaignPersistenceService persistenceService;

  public AdCampaignResponse create(
      Long companyId, Long adAccountId, CreateMetaCampaignRequest request) {
    AdAccount account = requireManagedAccount(companyId, adAccountId);
    String objective = normalizeObjective(request.objective());
    String buyingType =
        request.buyingType() == null || request.buyingType().isBlank()
            ? "AUCTION"
            : request.buyingType().trim().toUpperCase();
    if (!"AUCTION".equals(buyingType) && !"RESERVED".equals(buyingType)) {
      throw new BadRequestException("Unsupported Meta buying type: " + buyingType);
    }

    String accessToken = accessToken(companyId, account);
    String campaignId =
        apiClient.createCampaign(
            accessToken,
            account.getExternalAccountId(),
            request.name().trim(),
            objective,
            buyingType,
            trimToNull(request.bidStrategy()),
            request.dailyBudgetMinor(),
            request.lifetimeBudgetMinor(),
            request.specialAdCategories().stream()
                .map(value -> value.trim().toUpperCase())
                .distinct()
                .toList());

    return refresh(companyId, account, campaignId, accessToken);
  }

  public AdCampaignResponse update(
      Long companyId, Long campaignId, UpdateMetaCampaignRequest request) {
    AdCampaign campaign = requireCampaign(companyId, campaignId);
    AdAccount account = requireManagedAccount(companyId, campaign.getAdAccountId());
    String accessToken = accessToken(companyId, account);

    Map<String, Object> updates = new LinkedHashMap<>();
    if (request.name() != null) {
      if (request.name().isBlank()) throw new BadRequestException("Campaign name cannot be blank");
      updates.put("name", request.name().trim());
    }
    if (request.bidStrategy() != null) {
      if (request.bidStrategy().isBlank()) {
        throw new BadRequestException("Bid strategy cannot be blank");
      }
      updates.put("bid_strategy", request.bidStrategy().trim().toUpperCase());
    }
    if (request.dailyBudgetMinor() != null) updates.put("daily_budget", request.dailyBudgetMinor());
    if (request.lifetimeBudgetMinor() != null) updates.put("lifetime_budget", request.lifetimeBudgetMinor());
    if (updates.isEmpty()) throw new BadRequestException("No campaign changes were supplied");

    apiClient.updateCampaign(accessToken, campaign.getExternalCampaignId(), updates);
    return refresh(companyId, account, campaign.getExternalCampaignId(), accessToken);
  }

  public AdCampaignResponse setStatus(Long companyId, Long campaignId, String requestedStatus) {
    AdCampaign campaign = requireCampaign(companyId, campaignId);
    AdAccount account = requireManagedAccount(companyId, campaign.getAdAccountId());
    String status = requestedStatus == null ? "" : requestedStatus.trim().toUpperCase();
    if (!"ACTIVE".equals(status) && !"PAUSED".equals(status)) {
      throw new BadRequestException("Campaign status must be ACTIVE or PAUSED");
    }
    String accessToken = accessToken(companyId, account);
    apiClient.updateCampaign(
        accessToken, campaign.getExternalCampaignId(), Map.of("status", status));
    return refresh(companyId, account, campaign.getExternalCampaignId(), accessToken);
  }

  private AdCampaignResponse refresh(
      Long companyId, AdAccount account, String externalCampaignId, String accessToken) {
    var snapshot = apiClient.campaign(accessToken, externalCampaignId);
    return AdCampaignResponse.from(
        persistenceService.persistOne(companyId, account.getId(), snapshot));
  }

  private AdAccount requireManagedAccount(Long companyId, Long accountId) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.META_ADS) {
      throw new BadRequestException("Ad account is not a Meta Ads account");
    }
    if (!account.isManaged() || !account.isAccessible()) {
      throw new BadRequestException("Meta ad account is not enabled for management");
    }
    return account;
  }

  private AdCampaign requireCampaign(Long companyId, Long campaignId) {
    AdCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising campaign not found"));
    if (campaign.getProvider() != AdProvider.META_ADS || !campaign.isAccessible()) {
      throw new BadRequestException("Meta campaign is not available for management");
    }
    return campaign;
  }

  private String accessToken(Long companyId, AdAccount account) {
    var connection =
        connectionRepository
            .findByIdAndCompanyId(account.getConnectionId(), companyId)
            .orElseThrow(() -> new NotFoundException("Meta Ads connection not found"));
    if (connection.getStatus() != AdConnectionStatus.ACTIVE) {
      throw new BadRequestException("Meta Ads connection is not active");
    }
    return tokenService.read(connection.getId()).token().accessToken();
  }

  private String normalizeObjective(String value) {
    String objective = value.trim().toUpperCase();
    if (!SUPPORTED_OBJECTIVES.contains(objective)) {
      throw new BadRequestException("Unsupported Meta campaign objective: " + objective);
    }
    return objective;
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim().toUpperCase();
  }
}
