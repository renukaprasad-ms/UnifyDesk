package com.messaging.ads.meta;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.MetaDetailedTargetingSuggestion;
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
public class MetaAdsDetailedTargetingService {

  private final AdAccountRepository accountRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;

  public List<MetaDetailedTargetingSuggestion> searchInterests(
      Long companyId, Long accountId, String query, Integer limit) {
    if (query == null || query.isBlank()) {
      throw new BadRequestException("Meta interest search query is required");
    }
    Context context = context(companyId, accountId);
    JsonNode response =
        apiClient.searchAdInterests(
            context.accessToken(), query.trim(), limit == null ? 20 : limit);
    JsonNode data = response.path("data");
    if (!data.isArray()) return List.of();

    List<MetaDetailedTargetingSuggestion> result = new ArrayList<>();
    for (JsonNode item : data) {
      String id = text(item, "id");
      String name = text(item, "name");
      if (id == null || name == null) continue;
      result.add(
          new MetaDetailedTargetingSuggestion(
              id,
              name,
              "INTEREST",
              longValue(item, "audience_size_lower_bound"),
              longValue(item, "audience_size_upper_bound")));
    }
    return List.copyOf(result);
  }

  private Context context(Long companyId, Long accountId) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.META_ADS
        || !account.isManaged()
        || !account.isAccessible()) {
      throw new BadRequestException("Meta ad account is not enabled for management");
    }

    var connection =
        connectionRepository
            .findByIdAndCompanyId(account.getConnectionId(), companyId)
            .orElseThrow(() -> new NotFoundException("Meta Ads connection not found"));
    if (connection.getStatus() != AdConnectionStatus.ACTIVE) {
      throw new BadRequestException("Meta Ads connection is not active");
    }
    return new Context(
        tokenService.read(connection.getId()).token().accessToken());
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    String text = value.asText(null);
    return text == null || text.isBlank() ? null : text.trim();
  }

  private Long longValue(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() || !value.canConvertToLong()
        ? null
        : value.longValue();
  }

  private record Context(String accessToken) {}
}
