package com.messaging.ads.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdAudience;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.CustomAudienceSummary;
import com.messaging.ads.meta.dto.MetaAudienceSyncResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdAudienceRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetaAdsAudiencePersistenceService {
  private final AdAccountRepository accountRepository;
  private final AdAudienceRepository audienceRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public MetaAudienceSyncResponse persist(Long companyId, Long accountId, List<CustomAudienceSummary> snapshots) {
    AdAccount account = requireAccount(companyId, accountId);
    Map<String, AdAudience> existing = new HashMap<>();
    audienceRepository.findByCompanyIdAndAdAccountIdOrderByProviderUpdatedAtDesc(companyId, accountId)
        .forEach(item -> existing.put(item.getExternalAudienceId(), item));
    List<AdAudience> changed = new ArrayList<>();
    Instant syncedAt = Instant.now();
    int count = 0;
    for (CustomAudienceSummary snapshot : snapshots) {
      if (snapshot.id() == null || snapshot.id().isBlank()) continue;
      String externalId = snapshot.id().trim();
      changed.add(apply(account, existing.remove(externalId), snapshot, syncedAt));
      count++;
    }
    existing.values().forEach(item -> { item.setAccessible(false); item.setLastSyncAt(syncedAt); changed.add(item); });
    audienceRepository.saveAll(changed);
    return new MetaAudienceSyncResponse(count);
  }

  @Transactional
  public AdAudience persistOne(Long companyId, Long accountId, CustomAudienceSummary snapshot) {
    AdAccount account = requireAccount(companyId, accountId);
    if (snapshot.id() == null || snapshot.id().isBlank()) throw new BadRequestException("Meta audience snapshot is missing an ID");
    AdAudience existing = audienceRepository
        .findByCompanyIdAndAdAccountIdAndProviderAndExternalAudienceId(companyId, accountId, AdProvider.META_ADS, snapshot.id().trim())
        .orElse(null);
    return audienceRepository.save(apply(account, existing, snapshot, Instant.now()));
  }

  private AdAccount requireAccount(Long companyId, Long accountId) {
    AdAccount account = accountRepository.findByIdAndCompanyId(accountId, companyId)
        .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.META_ADS || !account.isAccessible()) throw new BadRequestException("Meta ad account is not available for audience synchronization");
    return account;
  }

  private AdAudience apply(AdAccount account, AdAudience value, CustomAudienceSummary snapshot, Instant syncedAt) {
    if (value == null) {
      value = new AdAudience();
      value.setCompanyId(account.getCompanyId());
      value.setAdAccountId(account.getId());
      value.setProvider(AdProvider.META_ADS);
      value.setExternalAudienceId(snapshot.id().trim());
    }
    value.setName(snapshot.name() == null || snapshot.name().isBlank() ? "Meta Audience " + snapshot.id() : snapshot.name().trim());
    value.setSubtype(blank(snapshot.subtype()));
    value.setDescription(blank(snapshot.description()));
    value.setRetentionDays(snapshot.retentionDays());
    value.setApproximateCountLowerBound(snapshot.approximateCountLowerBound());
    value.setApproximateCountUpperBound(snapshot.approximateCountUpperBound());
    value.setDeliveryStatusJson(jsonNode(snapshot.deliveryStatus()));
    value.setOperationStatusJson(jsonNode(snapshot.operationStatus()));
    value.setPixelId(blank(snapshot.pixelId()));
    value.setRuleJson(jsonNode(snapshot.rule()));
    value.setLookalikeSpecJson(jsonNode(snapshot.lookalikeSpec()));
    value.setProviderCreatedAt(parseInstant(snapshot.timeCreated()));
    value.setProviderUpdatedAt(parseInstant(snapshot.timeUpdated()));
    value.setAccessible(true);
    value.setProviderPayloadJson(json(snapshot));
    value.setLastSyncAt(syncedAt);
    return value;
  }

  private String blank(String value){ return value == null || value.isBlank() ? null : value.trim(); }
  private Instant parseInstant(String value){
    if(value==null || value.isBlank()) return null;
    try { return Instant.parse(value.trim()); }
    catch(DateTimeParseException ignored){ try { return OffsetDateTime.parse(value.trim()).toInstant(); } catch(DateTimeParseException ignoredAgain){ return null; } }
  }
  private String jsonNode(JsonNode value){ return value == null || value.isNull() ? null : json(value); }
  private String json(Object value){ try { return objectMapper.writeValueAsString(value); } catch(JsonProcessingException e){ throw new IllegalStateException("Unable to serialize Meta audience snapshot", e); } }
}
