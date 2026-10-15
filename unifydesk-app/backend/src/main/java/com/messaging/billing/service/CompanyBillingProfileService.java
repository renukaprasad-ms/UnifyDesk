package com.messaging.billing.service;

import com.messaging.billing.dto.CompanyBillingProfileDto;
import com.messaging.billing.entity.CompanyBillingProfile;
import com.messaging.billing.repository.CompanyBillingProfileRepository;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyBillingProfileService {

  private final CompanyBillingProfileRepository profileRepository;
  private final CompanyRepository companyRepository;

  @Transactional(readOnly = true)
  public CompanyBillingProfileDto getProfile(Long companyId) {
    return profileRepository
        .findByCompanyId(companyId)
        .map(this::toDto)
        .orElseGet(() -> defaultProfile(companyId));
  }

  @Transactional
  public CompanyBillingProfileDto updateProfile(Long companyId, CompanyBillingProfileDto dto) {
    CompanyBillingProfile profile =
        profileRepository
            .findByCompanyId(companyId)
            .orElseGet(
                () -> {
                  Company company =
                      companyRepository
                          .findById(companyId)
                          .orElseThrow(() -> new NotFoundException("Company not found: " + companyId));
                  CompanyBillingProfile newProfile = new CompanyBillingProfile();
                  newProfile.setCompany(company);
                  return newProfile;
                });

    profile.setLegalBusinessName(dto.legalBusinessName());
    profile.setTaxIdOrGstin(dto.taxIdOrGstin());
    profile.setBillingEmail(dto.billingEmail());
    profile.setBillingPhone(dto.billingPhone());
    profile.setAddressLine1(dto.addressLine1());
    profile.setAddressLine2(dto.addressLine2());
    profile.setCity(dto.city());
    profile.setState(dto.state());
    profile.setPostalCode(dto.postalCode());
    if (dto.country() != null && !dto.country().isBlank()) {
      profile.setCountry(dto.country());
    }

    return toDto(profileRepository.saveAndFlush(profile));
  }

  private CompanyBillingProfileDto defaultProfile(Long companyId) {
    Company company = companyRepository.findById(companyId).orElse(null);
    String defaultName = company != null ? company.getName() : "";
    return new CompanyBillingProfileDto(
        defaultName,
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "India");
  }

  private CompanyBillingProfileDto toDto(CompanyBillingProfile profile) {
    return new CompanyBillingProfileDto(
        profile.getLegalBusinessName(),
        profile.getTaxIdOrGstin(),
        profile.getBillingEmail(),
        profile.getBillingPhone(),
        profile.getAddressLine1(),
        profile.getAddressLine2(),
        profile.getCity(),
        profile.getState(),
        profile.getPostalCode(),
        profile.getCountry());
  }
}
