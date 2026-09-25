package com.messaging.emailtemplate.service;

import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.emailtemplate.dto.EmailTemplateAssetResponse;
import com.messaging.emailtemplate.entity.EmailTemplateAsset;
import com.messaging.emailtemplate.repository.EmailTemplateAssetRepository;
import com.messaging.media.dto.MediaAccessResponse;
import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaPurpose;
import com.messaging.media.service.MediaService;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailTemplateAssetService {

  private final EmailTemplateAssetRepository repository;
  private final MediaService mediaService;
  private final SecureRandom secureRandom = new SecureRandom();

  @Value("${APP_FRONTEND_BASE_URL:https://messaging.renukaprasad.in}")
  private String publicBaseUrl;

  @Transactional
  public EmailTemplateAssetResponse register(Long companyId, String mediaIdValue) {
    final long mediaId;
    try {
      mediaId = Long.parseLong(mediaIdValue);
    } catch (NumberFormatException exception) {
      throw new NotFoundException("Media not found");
    }

    Media media = mediaService.requireActiveCompanyMedia(mediaId, companyId);
    if (media.getPurpose() != MediaPurpose.TEMPLATE_MEDIA
        && media.getPurpose() != MediaPurpose.COMPANY_LOGO) {
      throw new BadRequestException("Only template media and company logos can be published");
    }

    EmailTemplateAsset asset =
        repository
            .findByCompanyIdAndMediaId(companyId, mediaId)
            .orElseGet(
                () -> {
                  EmailTemplateAsset created = new EmailTemplateAsset();
                  created.setCompanyId(companyId);
                  created.setMediaId(mediaId);
                  created.setPublicToken(token());
                  return repository.save(created);
                });
    return response(asset);
  }

  @Transactional(readOnly = true)
  public String resolve(String token) {
    EmailTemplateAsset asset =
        repository
            .findByPublicToken(token)
            .orElseThrow(() -> new NotFoundException("Email asset not found"));
    MediaAccessResponse access = mediaService.createTrustedAccessUrl(asset.getMediaId());
    return access.url();
  }

  private EmailTemplateAssetResponse response(EmailTemplateAsset asset) {
    String base =
        publicBaseUrl.endsWith("/")
            ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
            : publicBaseUrl;
    return new EmailTemplateAssetResponse(
        asset.getId().toString(),
        asset.getMediaId().toString(),
        base + "/api/v1/email-assets/" + asset.getPublicToken());
  }

  private String token() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
