package com.messaging.ads.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.dto.AdAccountResourceResponse;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdAccountResource;
import com.messaging.ads.enums.AdAccountResourceType;
import com.messaging.ads.meta.MetaAdsApiClient.InstagramSummary;
import com.messaging.ads.meta.MetaAdsApiClient.PageSummary;
import com.messaging.ads.meta.MetaAdsApiClient.PixelSummary;
import com.messaging.ads.meta.MetaAdsApiClient.ProductCatalogSummary;
import com.messaging.ads.meta.MetaAdsApiClient.ProductSetSummary;
import com.messaging.ads.meta.dto.MetaAdsResourceSyncResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdAccountResourceRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetaAdsResourcePersistenceService {

  private final AdAccountRepository accountRepository;
  private final AdAccountResourceRepository resourceRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public MetaAdsResourceSyncResponse persist(
      Long companyId,
      Long adAccountId,
      List<PageSummary> pages,
      List<InstagramSummary> instagramAccounts,
      List<PixelSummary> pixels,
      List<ProductCatalogSummary> catalogs,
      List<ProductSetSummary> productSets) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyIdForUpdate(adAccountId, companyId)
            .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (!account.isAccessible() || !account.isManaged()) {
      throw new BadRequestException(
          "Meta ad account is no longer available for resource synchronization");
    }

    Set<ResourceKey> discovered = new HashSet<>();

    pages.forEach(
        page ->
            upsert(
                account,
                AdAccountResourceType.FACEBOOK_PAGE,
                page.id(),
                page.name(),
                page,
                discovered));
    instagramAccounts.forEach(
        instagram ->
            upsert(
                account,
                AdAccountResourceType.INSTAGRAM_ACCOUNT,
                instagram.id(),
                instagram.username(),
                instagram,
                discovered));
    pixels.forEach(
        pixel ->
            upsert(
                account,
                AdAccountResourceType.META_PIXEL,
                pixel.id(),
                pixel.name(),
                pixel,
                discovered));
    catalogs.forEach(
        catalog ->
            upsert(
                account,
                AdAccountResourceType.META_PRODUCT_CATALOG,
                catalog.id(),
                catalog.name(),
                catalog,
                discovered));
    productSets.forEach(
        productSet ->
            upsert(
                account,
                AdAccountResourceType.META_PRODUCT_SET,
                productSet.id(),
                productSet.name(),
                productSet,
                discovered));

    List<AdAccountResource> existing =
        resourceRepository.findByCompanyIdAndAdAccountId(
            account.getCompanyId(), account.getId());
    existing.stream()
        .filter(
            resource ->
                !discovered.contains(
                    new ResourceKey(resource.getResourceType(), resource.getExternalResourceId())))
        .forEach(resource -> resource.setAccessible(false));
    resourceRepository.saveAll(existing);

    List<AdAccountResourceResponse> resources =
        resourceRepository
            .findByCompanyIdAndAdAccountIdOrderByResourceTypeAscNameAsc(
                account.getCompanyId(), account.getId())
            .stream()
            .map(AdAccountResourceResponse::from)
            .toList();

    return new MetaAdsResourceSyncResponse(
        pages.size(),
        instagramAccounts.size(),
        pixels.size(),
        catalogs.size(),
        productSets.size(),
        resources);
  }

  private void upsert(
      AdAccount account,
      AdAccountResourceType type,
      String externalId,
      String name,
      Object payload,
      Set<ResourceKey> discovered) {
    if (externalId == null || externalId.isBlank()) {
      return;
    }

    String normalizedId = externalId.trim();
    discovered.add(new ResourceKey(type, normalizedId));

    AdAccountResource resource =
        resourceRepository
            .findByCompanyIdAndAdAccountIdAndResourceTypeAndExternalResourceId(
                account.getCompanyId(), account.getId(), type, normalizedId)
            .orElseGet(AdAccountResource::new);

    if (resource.getId() == null) {
      resource.setCompanyId(account.getCompanyId());
      resource.setAdAccountId(account.getId());
      resource.setProvider(account.getProvider());
      resource.setResourceType(type);
      resource.setExternalResourceId(normalizedId);
    }

    resource.setName(name == null || name.isBlank() ? normalizedId : name.trim());
    resource.setAccessible(true);
    resource.setProviderPayloadJson(json(payload));
    resource.setLastSyncAt(Instant.now());
    resourceRepository.save(resource);
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Meta Ads account resource", exception);
    }
  }

  private record ResourceKey(AdAccountResourceType type, String externalId) {}
}
