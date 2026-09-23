package com.messaging.media.service;

import com.messaging.admin.enums.PlatformPermission;
import com.messaging.admin.service.PlatformAdminAuthorizationService;
import com.messaging.company.entity.Company;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.company.repository.CompanyRepository;
import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaPurpose;
import com.messaging.media.exception.MediaException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaAuthorizationService {

  private final CompanyRepository companyRepository;
  private final CompanyMemberRepository companyMemberRepository;
  private final PlatformAdminAuthorizationService platformAdminAuthorizationService;

  public void requireOwner(Media media, long userId) {
    if (!media.getOwnerUserId().equals(userId)) {
      throw new MediaException(HttpStatus.FORBIDDEN, "MEDIA_ACCESS_DENIED");
    }
  }

  public void requireReadAccess(Media media, long userId) {
    if (media.getOwnerUserId().equals(userId)) {
      return;
    }

    if (media.getCompanyId() != null && canReadCompanyMedia(media.getCompanyId(), userId)) {
      return;
    }

    if (media.getPurpose() == MediaPurpose.COMPANY_LOGO && canReadCompanyLogo(media, userId)) {
      return;
    }

    throw new MediaException(HttpStatus.FORBIDDEN, "MEDIA_ACCESS_DENIED");
  }

  private boolean canReadCompanyMedia(long companyId, long userId) {
    if (companyMemberRepository.existsByCompanyIdAndUserIdAndStatus(
        companyId, userId, CompanyMemberStatus.ACTIVE)) {
      return true;
    }
    return platformAdminAuthorizationService.canAccessCompany(userId, companyId)
        && platformAdminAuthorizationService.hasPermission(userId, PlatformPermission.COMPANY_VIEW);
  }

  private boolean canReadCompanyLogo(Media media, long userId) {
    Company company = companyRepository.findByLogoMediaId(media.getId()).orElse(null);
    if (company == null) {
      return false;
    }

    if (companyMemberRepository.existsByCompanyIdAndUserIdAndStatus(
        company.getId(), userId, CompanyMemberStatus.ACTIVE)) {
      return true;
    }

    return platformAdminAuthorizationService.canAccessCompany(userId, company.getId())
        && platformAdminAuthorizationService.hasPermission(userId, PlatformPermission.COMPANY_VIEW);
  }
}
