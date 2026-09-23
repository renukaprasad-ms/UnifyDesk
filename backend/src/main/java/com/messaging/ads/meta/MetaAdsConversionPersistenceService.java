package com.messaging.ads.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdConversion;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.CustomConversionSummary;
import com.messaging.ads.meta.dto.MetaConversionSyncResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdConversionRepository;
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
public class MetaAdsConversionPersistenceService {
  private final AdAccountRepository accountRepository;
  private final AdConversionRepository conversionRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public MetaConversionSyncResponse persist(Long companyId, Long accountId, List<CustomConversionSummary> snapshots) {
    AdAccount account = requireAccount(companyId, accountId);
    Map<String, AdConversion> existing = new HashMap<>();
    conversionRepository.findByCompanyIdAndAdAccountIdOrderByLastFiredAtDesc(companyId, accountId)
        .forEach(item -> existing.put(item.getExternalConversionId(), item));
    List<AdConversion> changed = new ArrayList<>();
    Instant syncedAt = Instant.now();
    int count = 0;
    for(CustomConversionSummary snapshot : snapshots){
      if(snapshot.id()==null || snapshot.id().isBlank()) continue;
      String externalId=snapshot.id().trim();
      changed.add(apply(account, existing.remove(externalId), snapshot, syncedAt));
      count++;
    }
    existing.values().forEach(item -> { item.setAccessible(false); item.setLastSyncAt(syncedAt); changed.add(item); });
    conversionRepository.saveAll(changed);
    return new MetaConversionSyncResponse(count);
  }

  @Transactional
  public AdConversion persistOne(Long companyId, Long accountId, CustomConversionSummary snapshot){
    AdAccount account=requireAccount(companyId, accountId);
    if(snapshot.id()==null || snapshot.id().isBlank()) throw new BadRequestException("Meta custom conversion snapshot is missing an ID");
    AdConversion existing=conversionRepository
        .findByCompanyIdAndAdAccountIdAndProviderAndExternalConversionId(companyId, accountId, AdProvider.META_ADS, snapshot.id().trim())
        .orElse(null);
    return conversionRepository.save(apply(account, existing, snapshot, Instant.now()));
  }

  private AdAccount requireAccount(Long companyId, Long accountId){
    AdAccount account=accountRepository.findByIdAndCompanyId(accountId, companyId).orElseThrow(() -> new NotFoundException("Ad account not found"));
    if(account.getProvider()!=AdProvider.META_ADS || !account.isAccessible()) throw new BadRequestException("Meta ad account is not available for conversion synchronization");
    return account;
  }

  private AdConversion apply(AdAccount account, AdConversion value, CustomConversionSummary snapshot, Instant syncedAt){
    if(value==null){ value=new AdConversion(); value.setCompanyId(account.getCompanyId()); value.setAdAccountId(account.getId()); value.setProvider(AdProvider.META_ADS); value.setExternalConversionId(snapshot.id().trim()); }
    value.setName(snapshot.name()==null || snapshot.name().isBlank()?"Meta Conversion "+snapshot.id():snapshot.name().trim());
    value.setDescription(blank(snapshot.description()));
    value.setCustomEventType(blank(snapshot.customEventType()));
    value.setEventSourceType(blank(snapshot.eventSourceType()));
    value.setPixelId(snapshot.pixel()==null?null:blank(snapshot.pixel().id()));
    value.setRuleJson(jsonNode(snapshot.rule()));
    value.setDataSourcesJson(jsonNode(snapshot.dataSources()));
    value.setDefaultConversionValue(snapshot.defaultConversionValue());
    value.setArchived(Boolean.TRUE.equals(snapshot.archived()));
    value.setUnavailable(Boolean.TRUE.equals(snapshot.unavailable()));
    value.setFirstFiredAt(parseInstant(snapshot.firstFiredTime()));
    value.setLastFiredAt(parseInstant(snapshot.lastFiredTime()));
    value.setProviderCreatedAt(parseInstant(snapshot.creationTime()));
    value.setAccessible(true);
    value.setProviderPayloadJson(json(snapshot));
    value.setLastSyncAt(syncedAt);
    return value;
  }

  private String blank(String value){ return value==null || value.isBlank()?null:value.trim(); }
  private Instant parseInstant(String value){ if(value==null||value.isBlank()) return null; try{return Instant.parse(value.trim());}catch(DateTimeParseException ignored){try{return OffsetDateTime.parse(value.trim()).toInstant();}catch(DateTimeParseException ignoredAgain){return null;}} }
  private String jsonNode(JsonNode value){ return value==null || value.isNull()?null:json(value); }
  private String json(Object value){ try{return objectMapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("Unable to serialize Meta conversion snapshot", e);} }
}
