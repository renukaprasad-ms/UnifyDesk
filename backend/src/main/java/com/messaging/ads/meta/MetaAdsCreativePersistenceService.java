package com.messaging.ads.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdCreative;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.CreativeSummary;
import com.messaging.ads.meta.dto.MetaCreativeSyncResponse;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.ads.repository.AdCreativeRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
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
public class MetaAdsCreativePersistenceService {

  private static final DateTimeFormatter META_OFFSET_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

  private final AdAccountRepository accountRepository;
  private final AdCreativeRepository creativeRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public MetaCreativeSyncResponse persist(
      Long companyId, Long accountId, List<CreativeSummary> snapshots) {
    AdAccount account = requireAccount(companyId, accountId);
    Map<String, AdCreative> existing = new HashMap<>();
    creativeRepository
        .findByCompanyIdAndAdAccountIdOrderByProviderUpdatedAtDesc(companyId, accountId)
        .forEach(item -> existing.put(item.getExternalCreativeId(), item));

    List<AdCreative> changed = new ArrayList<>();
    Instant syncedAt = Instant.now();
    int count = 0;
    for (CreativeSummary snapshot : snapshots) {
      if (snapshot.id() == null || snapshot.id().isBlank()) continue;
      String externalId = snapshot.id().trim();
      changed.add(applySnapshot(account, existing.remove(externalId), snapshot, syncedAt));
      count++;
    }
    existing.values().forEach(
        item -> {
          item.setAccessible(false);
          item.setLastSyncAt(syncedAt);
          changed.add(item);
        });
    creativeRepository.saveAll(changed);
    return new MetaCreativeSyncResponse(count);
  }

  @Transactional
  public AdCreative persistOne(Long companyId, Long accountId, CreativeSummary snapshot) {
    AdAccount account = requireAccount(companyId, accountId);
    if (snapshot.id() == null || snapshot.id().isBlank()) {
      throw new BadRequestException("Meta creative snapshot is missing an ID");
    }
    AdCreative existing =
        creativeRepository
            .findByCompanyIdAndAdAccountIdAndProviderAndExternalCreativeId(
                companyId, accountId, AdProvider.META_ADS, snapshot.id().trim())
            .orElse(null);
    return creativeRepository.save(
        applySnapshot(account, existing, snapshot, Instant.now()));
  }

  private AdAccount requireAccount(Long companyId, Long accountId) {
    AdAccount account = accountRepository.findByIdAndCompanyId(accountId, companyId)
        .orElseThrow(() -> new NotFoundException("Ad account not found"));
    if (account.getProvider() != AdProvider.META_ADS || !account.isAccessible()) {
      throw new BadRequestException("Meta ad account is not available for creative synchronization");
    }
    return account;
  }

  private AdCreative applySnapshot(
      AdAccount account, AdCreative creative, CreativeSummary snapshot, Instant syncedAt) {
    if (creative == null) {
      creative = new AdCreative();
      creative.setCompanyId(account.getCompanyId());
      creative.setAdAccountId(account.getId());
      creative.setProvider(AdProvider.META_ADS);
      creative.setExternalCreativeId(snapshot.id().trim());
    }
    JsonNode story = snapshot.objectStorySpec();
    JsonNode linkData = story == null ? null : story.path("link_data");
    JsonNode videoData = story == null ? null : story.path("video_data");
    JsonNode attachments = linkData == null ? null : linkData.path("child_attachments");

    String format =
        attachments != null && attachments.isArray() && attachments.size() > 0
            ? "CAROUSEL"
            : videoData != null && !videoData.isMissingNode() && !videoData.isEmpty()
                ? "VIDEO"
                : "SINGLE_IMAGE";

    creative.setName(blankFallback(snapshot.name(), "Meta Creative " + snapshot.id()));
    creative.setCreativeFormat(format);
    creative.setPageId(text(story, "page_id"));
    String instagram = text(story, "instagram_actor_id");
    if (instagram == null) instagram = text(story, "instagram_user_id");
    creative.setInstagramActorId(instagram);
    JsonNode data = "VIDEO".equals(format) ? videoData : linkData;
    creative.setPrimaryText(text(data, "message"));
    creative.setHeadline(firstNonBlank(text(data, "name"), snapshot.title()));
    creative.setDescription(text(data, "description"));
    creative.setCallToActionType(firstNonBlank(
        text(data == null ? null : data.path("call_to_action"), "type"),
        snapshot.callToActionType()));
    creative.setDestinationUrl(destination(data));
    creative.setImageHash(text(data, "image_hash"));
    creative.setImageUrl(firstNonBlank(text(data, "picture"), snapshot.thumbnailUrl()));
    creative.setVideoId(text(data, "video_id"));
    creative.setUrlTags(snapshot.urlTags());
    creative.setObjectStorySpecJson(jsonNode(story));
    creative.setCarouselJson(
        attachments != null && attachments.isArray() ? jsonNode(attachments) : null);
    creative.setProviderCreatedAt(parseInstant(snapshot.createdTime()));
    creative.setProviderUpdatedAt(parseInstant(snapshot.updatedTime()));
    creative.setAccessible(true);
    creative.setProviderPayloadJson(json(snapshot));
    creative.setLastSyncAt(syncedAt);
    return creative;
  }

  private String destination(JsonNode data) {
    String direct = text(data, "link");
    if (direct != null) return direct;
    JsonNode cta = data == null ? null : data.path("call_to_action");
    JsonNode value = cta == null ? null : cta.path("value");
    return text(value, "link");
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.isMissingNode() || node.isNull()) return null;
    JsonNode value = node.get(field);
    return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
  }

  private String firstNonBlank(String first, String second) {
    return first != null && !first.isBlank() ? first : second;
  }

  private String blankFallback(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private Instant parseInstant(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Instant.parse(value.trim());
    } catch (DateTimeParseException ignored) {
      try {
        return OffsetDateTime.parse(value.trim(), META_OFFSET_TIME).toInstant();
      } catch (DateTimeParseException ignoredAgain) {
        return null;
      }
    }
  }

  private String jsonNode(JsonNode value) {
    return value == null || value.isNull() ? null : json(value);
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Meta creative snapshot", exception);
    }
  }
}
