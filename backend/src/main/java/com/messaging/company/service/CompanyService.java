package com.messaging.company.service;

import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.dto.CompanyResponse;
import com.messaging.company.dto.CreateCompanyRequest;
import com.messaging.company.entity.Company;
import com.messaging.company.entity.CompanyMember;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.enums.CompanyRole;
import com.messaging.company.enums.CompanyStatus;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.company.repository.CompanyRepository;
import com.messaging.security.service.CurrentUserService;
import com.messaging.user.entity.User;
import com.messaging.user.repository.UserRepository;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyService {

  private final CompanyRepository companyRepository;
  private final CompanyMemberRepository memberRepository;
  private final UserRepository userRepository;
  private final CurrentUserService currentUserService;

  @Transactional
  public CompanyResponse create(CreateCompanyRequest request) {
    Long userId = currentUserService.currentUserId();
    User user =
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    Company company = new Company();
    company.setName(request.name().trim());
    company.setSlug(uniqueSlug(request.name()));
    company.setBusinessEmail(normalizeOptional(request.businessEmail()));
    company.setCountry(normalizeOptional(request.country()));
    company.setTimezone(normalizeOptional(request.timezone()));
    company.setStatus(CompanyStatus.ACTIVE);
    company.setCreatedBy(user);
    company = companyRepository.saveAndFlush(company);

    CompanyMember owner = new CompanyMember();
    owner.setCompany(company);
    owner.setUser(user);
    owner.setRole(CompanyRole.OWNER);
    owner.setStatus(CompanyMemberStatus.ACTIVE);
    memberRepository.saveAndFlush(owner);
    return toResponse(company, CompanyRole.OWNER);
  }

  @Transactional(readOnly = true)
  public List<CompanyResponse> myCompanies() {
    Long userId = currentUserService.currentUserId();
    return companiesForUser(userId);
  }

  @Transactional(readOnly = true)
  public List<CompanyResponse> companiesForUser(Long userId) {
    return memberRepository.findByUserIdAndStatus(userId, CompanyMemberStatus.ACTIVE).stream()
        .map(member -> toResponse(member.getCompany(), member.getRole()))
        .toList();
  }

  @Transactional(readOnly = true)
  public long companyCountForUser(Long userId) {
    return memberRepository.countByUserIdAndStatus(userId, CompanyMemberStatus.ACTIVE);
  }

  @Transactional(readOnly = true)
  public Company getCompany(Long companyId) {
    return companyRepository
        .findById(companyId)
        .orElseThrow(() -> new NotFoundException("Company not found"));
  }

  private CompanyResponse toResponse(Company company, CompanyRole role) {
    return new CompanyResponse(
        company.getId().toString(),
        company.getName(),
        company.getSlug(),
        company.getBusinessEmail(),
        company.getCountry(),
        company.getTimezone(),
        company.getStatus(),
        role);
  }

  private String normalizeOptional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private String uniqueSlug(String name) {
    String base = slugify(name);
    String slug = base;
    int suffix = 2;
    while (companyRepository.existsBySlug(slug)) {
      slug = base + "-" + suffix++;
    }
    return slug;
  }

  private String slugify(String value) {
    String slug =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
    if (slug.isBlank()) {
      throw new BadRequestException("Company name must contain letters or numbers");
    }
    return slug.length() <= 120 ? slug : slug.substring(0, 120).replaceAll("-$", "");
  }
}
