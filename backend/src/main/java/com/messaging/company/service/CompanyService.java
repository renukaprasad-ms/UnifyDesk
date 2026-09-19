package com.messaging.company.service;

import com.messaging.billing.service.DefaultPlanAssignmentService;
import com.messaging.channel.dto.ChannelConnectionResponse;
import com.messaging.channel.service.ChannelConnectionService;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.dto.CompanyResponse;
import com.messaging.company.dto.CompanyUserResponse;
import com.messaging.company.dto.CreateCompanyRequest;
import com.messaging.company.dto.UpdateCompanyRequest;
import com.messaging.company.entity.Company;
import com.messaging.company.entity.CompanyMember;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.enums.CompanyRole;
import com.messaging.company.enums.CompanyStatus;
import com.messaging.company.repository.CompanyCustomRoleRepository;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.company.repository.CompanyMemberRoleAssignmentRepository;
import com.messaging.company.repository.CompanyRepository;
import com.messaging.lifecycle.enums.LifecycleEntityType;
import com.messaging.lifecycle.service.DeletionQueueService;
import com.messaging.media.entity.Media;
import com.messaging.media.enums.MediaPurpose;
import com.messaging.media.service.MediaService;
import com.messaging.security.service.CurrentUserService;
import com.messaging.user.entity.User;
import com.messaging.user.repository.UserRepository;
import java.text.Normalizer;
import java.time.Instant;
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
  private final CompanyMemberRoleAssignmentRepository roleAssignmentRepository;
  private final CompanyCustomRoleRepository customRoleRepository;
  private final UserRepository userRepository;
  private final CurrentUserService currentUserService;
  private final MediaService mediaService;
  private final DefaultPlanAssignmentService defaultPlanAssignmentService;
  private final ChannelConnectionService channelConnectionService;
  private final DeletionQueueService deletionQueueService;

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
    company.setLogoMediaId(resolveLogoMediaId(request.logoMediaId(), userId));
    company.setStatus(CompanyStatus.ACTIVE);
    company.setCreatedBy(user);
    company = companyRepository.saveAndFlush(company);

    CompanyMember owner = new CompanyMember();
    owner.setCompany(company);
    owner.setUser(user);
    owner.setRole(CompanyRole.OWNER);
    owner.setStatus(CompanyMemberStatus.ACTIVE);
    memberRepository.saveAndFlush(owner);

    defaultPlanAssignmentService.assignToNewCompany(company);
    if (company.getLogoMediaId() != null) {
      mediaService.assignCompanyMedia(company.getLogoMediaId(), company.getId(), userId);
    }
    return toResponse(company, CompanyRole.OWNER);
  }

  @Transactional(readOnly = true)
  public CompanyResponse companyForUser(Long companyId, Long userId) {
    Company company = getCompany(companyId);
    CompanyRole role =
        memberRepository
            .findByCompanyIdAndUserIdAndStatus(companyId, userId, CompanyMemberStatus.ACTIVE)
            .map(CompanyMember::getRole)
            .orElse(null);
    return toResponse(company, role);
  }

  @Transactional
  public CompanyResponse update(Long companyId, Long userId, UpdateCompanyRequest request) {
    Company company = getCompany(companyId);
    company.setName(request.name().trim());
    company.setBusinessEmail(normalizeOptional(request.businessEmail()));
    company.setCountry(normalizeOptional(request.country()));
    company.setTimezone(normalizeOptional(request.timezone()));
    company.setLogoMediaId(resolveLogoMediaId(request.logoMediaId(), userId));
    company = companyRepository.saveAndFlush(company);
    if (company.getLogoMediaId() != null) {
      mediaService.assignCompanyMedia(company.getLogoMediaId(), company.getId(), userId);
    }

    CompanyRole role =
        memberRepository
            .findByCompanyIdAndUserIdAndStatus(companyId, userId, CompanyMemberStatus.ACTIVE)
            .map(CompanyMember::getRole)
            .orElse(null);
    return toResponse(company, role);
  }

  @Transactional
  public void delete(Long companyId, Long actorUserId) {
    Company company = getCompany(companyId);
    if (company.getStatus() == CompanyStatus.DELETED) {
      return;
    }

    Instant now = Instant.now();

    List<ChannelConnectionResponse> connections = channelConnectionService.list(companyId, null);
    for (ChannelConnectionResponse connection : connections) {
      channelConnectionService.disconnect(
          companyId, Long.valueOf(connection.id()), actorUserId);
    }

    var assignments = roleAssignmentRepository.findByCompanyIdAndRevokedAtIsNull(companyId);
    assignments.forEach(assignment -> assignment.setRevokedAt(now));
    roleAssignmentRepository.saveAll(assignments);

    var roles = customRoleRepository.findByCompanyIdAndDeletedAtIsNull(companyId);
    roles.forEach(
        role -> {
          role.setDeletedAt(now);
          role.setDeletedByUserId(actorUserId);
        });
    customRoleRepository.saveAll(roles);

    var members = memberRepository.findByCompanyId(companyId);
    members.forEach(member -> member.setStatus(CompanyMemberStatus.LEFT));
    memberRepository.saveAll(members);

    company.setStatus(CompanyStatus.DELETED);
    company.setDeletedAt(now);
    company.setName("Deleted company");
    company.setSlug("deleted-" + companyId);
    company.setBusinessEmail(null);
    company.setCountry(null);
    company.setTimezone(null);
    company.setLogoMediaId(null);
    companyRepository.saveAndFlush(company);

    deletionQueueService.enqueue(
        LifecycleEntityType.COMPANY, companyId, companyId, actorUserId);
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
  public List<CompanyUserResponse> companyUsers(Long companyId) {
    getCompany(companyId);
    return memberRepository.findByCompanyIdAndStatus(companyId, CompanyMemberStatus.ACTIVE).stream()
        .map(this::toCompanyUserResponse)
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
        company.getLogoMediaId() == null ? null : company.getLogoMediaId().toString(),
        company.getStatus(),
        role);
  }

  private CompanyUserResponse toCompanyUserResponse(CompanyMember member) {
    User user = member.getUser();
    return new CompanyUserResponse(
        member.getId().toString(),
        user.getId().toString(),
        user.getName(),
        user.getUsername(),
        user.getEmail(),
        user.isVerified(),
        user.isTwoFactorEnabled(),
        member.getRole(),
        member.getStatus(),
        member.getCreatedAt(),
        roleAssignmentRepository
            .findActive(member.getCompany().getId(), user.getId(), java.time.Instant.now())
            .stream()
            .map(assignment -> assignment.getRole().getName())
            .sorted()
            .toList());
  }

  private Long resolveLogoMediaId(String logoMediaId, long userId) {
    if (logoMediaId == null || logoMediaId.isBlank()) {
      return null;
    }
    long parsedLogoMediaId;
    try {
      parsedLogoMediaId = Long.parseLong(logoMediaId);
    } catch (NumberFormatException exception) {
      throw new BadRequestException("Invalid company logo media id");
    }
    Media media = mediaService.getActiveOwnedMedia(parsedLogoMediaId, userId);
    if (media.getPurpose() != MediaPurpose.COMPANY_LOGO) {
      throw new BadRequestException("Invalid company logo media purpose");
    }
    return parsedLogoMediaId;
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
