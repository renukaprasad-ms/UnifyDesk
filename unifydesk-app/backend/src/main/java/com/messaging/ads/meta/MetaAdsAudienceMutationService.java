package com.messaging.ads.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.dto.AdAudienceResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdAudience;
import com.messaging.ads.enums.AdAccountResourceType;
import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.dto.CreateMetaAudienceRequest;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdAccountResourceRepository;
import com.messaging.ads.repository.AdAudienceRepository;
import com.messaging.ads.repository.AdProviderConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetaAdsAudienceMutationService {
  private final AdAccountRepository accountRepository;
  private final AdAccountResourceRepository resourceRepository;
  private final AdAudienceRepository audienceRepository;
  private final AdProviderConnectionRepository connectionRepository;
  private final MetaAdsTokenService tokenService;
  private final MetaAdsApiClient apiClient;
  private final MetaAdsAudiencePersistenceService persistenceService;
  private final ObjectMapper objectMapper;

  public AdAudienceResponse create(Long companyId, Long accountId, CreateMetaAudienceRequest request){
    AdAccount account=requireAccount(companyId,accountId);
    String type=request.type().trim().toUpperCase();
    Map<String,Object> payload=new LinkedHashMap<>();
    payload.put("name",request.name().trim());
    payload.put("subtype",type);
    if(request.description()!=null&&!request.description().isBlank()) payload.put("description",request.description().trim());

    if("WEBSITE".equals(type)){
      validatePixel(companyId,accountId,request.pixelId());
      validateJson(request.ruleJson(),"Audience rule");
      payload.put("pixel_id",request.pixelId().trim());
      payload.put("rule",request.ruleJson().trim());
      payload.put("retention_days",request.retentionDays()==null?30:request.retentionDays());
    } else if("LOOKALIKE".equals(type)){
      AdAudience origin=requireOrigin(companyId,accountId,request.originAudienceId());
      Map<String,Object> spec=new LinkedHashMap<>();
      spec.put("type","similarity");
      spec.put("country",request.country().trim().toUpperCase());
      spec.put("ratio",request.ratio());
      payload.put("origin_audience_id",origin.getExternalAudienceId());
      payload.put("lookalike_spec",json(spec));
    } else {
      throw new BadRequestException("Supported audience types are WEBSITE and LOOKALIKE");
    }

    String token=token(companyId,account);
    String externalId=apiClient.createCustomAudience(token,account.getExternalAccountId(),payload);
    return AdAudienceResponse.from(
        persistenceService.persistOne(companyId,accountId,apiClient.customAudience(token,externalId)));
  }

  private AdAudience requireOrigin(Long companyId,Long accountId,String value){
    Long id;
    try{id=Long.valueOf(value);}catch(NumberFormatException e){throw new BadRequestException("Source audience ID is invalid");}
    AdAudience audience=audienceRepository.findByIdAndCompanyId(id,companyId).orElseThrow(()->new NotFoundException("Source audience not found"));
    if(!accountId.equals(audience.getAdAccountId())||audience.getProvider()!=AdProvider.META_ADS||!audience.isAccessible()) throw new BadRequestException("Source audience does not belong to this Meta ad account");
    return audience;
  }

  private void validatePixel(Long companyId,Long accountId,String pixelId){
    var pixel=resourceRepository.findByCompanyIdAndAdAccountIdAndResourceTypeAndExternalResourceId(
        companyId,accountId,AdAccountResourceType.META_PIXEL,pixelId.trim())
        .orElseThrow(()->new BadRequestException("Selected Meta pixel is not available for this ad account"));
    if(!pixel.isAccessible()) throw new BadRequestException("Selected Meta pixel is no longer accessible");
  }

  private void validateJson(String value,String label){
    try{objectMapper.readTree(value);}catch(JsonProcessingException e){throw new BadRequestException(label+" must be valid JSON");}
  }

  private String json(Object value){try{return objectMapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("Unable to serialize lookalike specification",e);}}

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
