package com.messaging.ads.meta;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.MetaAdStudyResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaAdsExperimentService {

  private final AdAccountRepository accountRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;

  public List<MetaAdStudyResponse> studies(Long companyId, Long accountId) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.META_ADS
        || !account.isManaged()
        || !account.isAccessible()) {
      throw new BadRequestException(
          "Meta ad account is not enabled for management");
    }
    if (account.getExternalBusinessId() == null
        || account.getExternalBusinessId().isBlank()) {
      return List.of();
    }

    var connection =
        connectionRepository
            .findByIdAndCompanyId(account.getConnectionId(), companyId)
            .orElseThrow(() -> new NotFoundException("Meta Ads connection not found"));
    if (connection.getStatus() != AdConnectionStatus.ACTIVE) {
      throw new BadRequestException("Meta Ads connection is not active");
    }

    String token = tokenService.read(connection.getId()).token().accessToken();
    JsonNode response =
        apiClient.adStudies(token, account.getExternalBusinessId());
    JsonNode data = response == null ? null : response.path("data");
    if (data == null || !data.isArray()) return List.of();

    List<MetaAdStudyResponse> studies = new ArrayList<>();
    for (JsonNode node : data) {
      studies.add(
          new MetaAdStudyResponse(
              text(node, "id"),
              text(node, "name"),
              text(node, "description"),
              text(node, "type"),
              longOrNull(node, "start_time"),
              longOrNull(node, "end_time"),
              longOrNull(node, "observation_end_time"),
              longOrNull(node, "results_first_available_date"),
              text(node, "confidence_level"),
              longOrNull(node, "created_time"),
              longOrNull(node, "updated_time")));
    }
    return List.copyOf(studies);
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    String result = value.asText(null);
    return result == null || result.isBlank() ? null : result.trim();
  }

  private Long longOrNull(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    if (value.isNumber()) return value.asLong();
    try {
      return Long.valueOf(value.asText());
    } catch (NumberFormatException ignored) {
      return null;
    }
  }
}
