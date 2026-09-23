package com.messaging.ads.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.dto.AdConversionResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdAccountResourceType;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.CreateMetaConversionRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdAccountResourceRepository;
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
public class MetaAdsConversionMutationService {
  private static final Set<String> EVENT_TYPES=Set.of(
      "ADD_PAYMENT_INFO","ADD_TO_CART","ADD_TO_WISHLIST","COMPLETE_REGISTRATION","CONTACT",
      "CONTENT_VIEW","CUSTOMIZE_PRODUCT","DONATE","FACEBOOK_SELECTED","FIND_LOCATION",
      "INITIATED_CHECKOUT","LEAD","LISTING_INTERACTION","OTHER","PURCHASE","SCHEDULE",
      "SEARCH","START_TRIAL","SUBMIT_APPLICATION","SUBSCRIBE");

  private final AdAccountRepository accountRepository;
  private final AdAccountResourceRepository resourceRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;
  private final MetaAdsConversionPersistenceService persistenceService;
  private final ObjectMapper objectMapper;

  public AdConversionResponse create(Long companyId,Long accountId,CreateMetaConversionRequest request){
    AdAccount account=requireAccount(companyId,accountId);
    validatePixel(companyId,accountId,request.pixelId());
    validateJson(request.ruleJson());
    String eventType=request.customEventType().trim().toUpperCase();
    if(!EVENT_TYPES.contains(eventType)) throw new BadRequestException("Unsupported Meta custom event type");

    Map<String,Object> payload=new LinkedHashMap<>();
    payload.put("name",request.name().trim());
    payload.put("custom_event_type",eventType);
    payload.put("event_source_id",request.pixelId().trim());
    payload.put("action_source_type","website");
    payload.put("rule",request.ruleJson().trim());
    if(request.description()!=null&&!request.description().isBlank()) payload.put("description",request.description().trim());
    if(request.defaultConversionValue()!=null) payload.put("default_conversion_value",request.defaultConversionValue());

    String token=token(companyId,account);
    String externalId=apiClient.createCustomConversion(token,account.getExternalAccountId(),payload);
    return AdConversionResponse.from(
        persistenceService.persistOne(companyId,accountId,apiClient.customConversion(token,externalId)));
  }

  private void validatePixel(Long companyId,Long accountId,String pixelId){
    var pixel=resourceRepository.findByCompanyIdAndAdAccountIdAndResourceTypeAndExternalResourceId(
        companyId,accountId,AdAccountResourceType.META_PIXEL,pixelId.trim())
        .orElseThrow(()->new BadRequestException("Selected Meta pixel is not available for this ad account"));
    if(!pixel.isAccessible()) throw new BadRequestException("Selected Meta pixel is no longer accessible");
  }
  private void validateJson(String value){try{objectMapper.readTree(value);}catch(JsonProcessingException e){throw new BadRequestException("Conversion rule must be valid JSON");}}
  private AdAccount requireAccount(Long companyId,Long accountId){
    AdAccount account=accountRepository.findByIdAndCompanyId(accountId,companyId).orElseThrow(()->new NotFoundException("Ad account not found"));
    if(account.getProvider()!=AdProvider.META_ADS||!account.isManaged()||!account.isAccessible()) throw new BadRequestException("Meta ad account is not enabled for management");
    return account;
  }
  private String token(Long companyId,AdAccount account){
    var connection=connectionRepository.findByIdAndCompanyId(account.getConnectionId(),companyId).orElseThrow(()->new NotFoundException("Meta Ads connection not found"));
    if(connection.getStatus()!=AdConnectionStatus.ACTIVE) throw new BadRequestException("Meta Ads connection is not active");
    return tokenService.read(connection.getId()).token().accessToken();
  }
}
