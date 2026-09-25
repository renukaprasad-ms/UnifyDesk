package com.messaging.ads.campaign.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.dto.CreateCreativePackRequest;
import com.messaging.ads.campaign.dto.CreateCreativeVariantRequest;
import com.messaging.ads.campaign.dto.CreativePackAssetInput;
import com.messaging.ads.campaign.entity.CreativePack;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.CreativeAssetRole;
import com.messaging.ads.campaign.enums.CreativeVariantType;
import com.messaging.ads.campaign.enums.ReachlyoCampaignStatus;
import com.messaging.ads.campaign.repository.CampaignChannelRepository;
import com.messaging.ads.campaign.repository.CreativePackAssetRepository;
import com.messaging.ads.campaign.repository.CreativePackRepository;
import com.messaging.ads.campaign.repository.CreativeVariantRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaPurpose;
import com.messaging.media.enums.MediaStatus;
import com.messaging.media.enums.MediaType;
import com.messaging.media.repository.MediaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreativePackServiceTests {

  @Mock private ReachlyoCampaignRepository campaignRepository;
  @Mock private CampaignChannelRepository channelRepository;
  @Mock private CreativePackRepository packRepository;
  @Mock private CreativePackAssetRepository assetRepository;
  @Mock private CreativeVariantRepository variantRepository;
  @Mock private MediaRepository mediaRepository;

  private CreativePackService service;

  @BeforeEach
  void setUp() {
    service =
        new CreativePackService(
            campaignRepository,
            channelRepository,
            packRepository,
            assetRepository,
            variantRepository,
            mediaRepository,
            new ObjectMapper());
  }

  @Test
  void rejectsNonAdCreativeVariantMedia() {
    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(10L);
    campaign.setCompanyId(1L);
    campaign.setStatus(ReachlyoCampaignStatus.DRAFT);
    when(campaignRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(campaign));

    CreativePack pack = new CreativePack();
    pack.setId(20L);
    pack.setCompanyId(1L);
    pack.setReachlyoCampaignId(10L);
    when(packRepository.findByIdAndCompanyIdAndReachlyoCampaignId(20L, 1L, 10L))
        .thenReturn(Optional.of(pack));

    Media media = new Media();
    media.setId(30L);
    media.setCompanyId(1L);
    media.setStatus(MediaStatus.ACTIVE);
    media.setPurpose(MediaPurpose.MESSAGE_ATTACHMENT);
    media.setMediaType(MediaType.IMAGE);
    when(mediaRepository.findByIdAndCompanyId(30L, 1L)).thenReturn(Optional.of(media));

    assertThatThrownBy(
            () ->
                service.createVariant(
                    1L,
                    10L,
                    20L,
                    new CreateCreativeVariantRequest(
                        CreativeVariantType.META_STORY,
                        null,
                        "Story creative",
                        "story",
                        "Primary",
                        List.of("Headline"),
                        List.of("Description"),
                        "LEARN_MORE",
                        "https://example.com",
                        "{\"mediaId\":\"30\"}")))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("AD_CREATIVE");
  }

  @Test
  void rejectsNonAdCreativeMedia() {
    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(10L);
    campaign.setCompanyId(1L);
    campaign.setStatus(ReachlyoCampaignStatus.DRAFT);
    when(campaignRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(campaign));
    when(packRepository.save(any(CreativePack.class)))
        .thenAnswer(
            invocation -> {
              CreativePack pack = invocation.getArgument(0);
              pack.setId(20L);
              return pack;
            });

    Media media = new Media();
    media.setId(30L);
    media.setCompanyId(1L);
    media.setStatus(MediaStatus.ACTIVE);
    media.setPurpose(MediaPurpose.MESSAGE_ATTACHMENT);
    media.setMediaType(MediaType.IMAGE);
    when(mediaRepository.findByIdAndCompanyId(30L, 1L)).thenReturn(Optional.of(media));

    assertThatThrownBy(
            () ->
                service.create(
                    1L,
                    99L,
                    10L,
                    new CreateCreativePackRequest(
                        "Pack",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(
                            new CreativePackAssetInput(
                                30L, CreativeAssetRole.IMAGE, null, 0)))))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("AD_CREATIVE");
  }
}
