package com.messaging.ads.campaign.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.dto.CreateAudienceDefinitionRequest;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.ReachlyoCampaignStatus;
import com.messaging.ads.campaign.repository.AudienceDefinitionRepository;
import com.messaging.ads.campaign.repository.AudienceMappingRepository;
import com.messaging.ads.campaign.repository.CampaignChannelRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.ads.repository.AdAudienceRepository;
import com.messaging.common.exception.BadRequestException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AudienceDefinitionServiceTests {

  @Mock private ReachlyoCampaignRepository campaignRepository;
  @Mock private AudienceDefinitionRepository definitionRepository;
  @Mock private AudienceMappingRepository mappingRepository;
  @Mock private CampaignChannelRepository channelRepository;
  @Mock private AdAudienceRepository adAudienceRepository;

  private AudienceDefinitionService service;

  @BeforeEach
  void setUp() {
    service =
        new AudienceDefinitionService(
            campaignRepository,
            definitionRepository,
            mappingRepository,
            channelRepository,
            adAudienceRepository,
            new ObjectMapper());
  }

  @Test
  void rejectsInvalidAgeRange() {
    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(10L);
    campaign.setCompanyId(1L);
    campaign.setStatus(ReachlyoCampaignStatus.DRAFT);
    when(campaignRepository.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(campaign));

    assertThatThrownBy(
            () ->
                service.create(
                    1L,
                    99L,
                    10L,
                    new CreateAudienceDefinitionRequest(
                        "Audience",
                        null,
                        50,
                        30,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of())))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("maximum age");
  }
}
