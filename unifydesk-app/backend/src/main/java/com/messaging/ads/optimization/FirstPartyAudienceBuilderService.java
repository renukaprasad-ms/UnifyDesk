package com.messaging.ads.optimization;

import com.messaging.ads.dto.AdAudienceResponse;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.GoogleAdsAudienceMutationService;
import com.messaging.ads.google.GoogleDataManagerService;
import com.messaging.ads.google.dto.CreateGoogleAudienceRequest;
import com.messaging.ads.google.dto.GoogleCustomerMatchMember;
import com.messaging.ads.google.dto.GoogleCustomerMatchUploadRequest;
import com.messaging.ads.meta.MetaAdsAudienceMutationService;
import com.messaging.ads.meta.MetaAdsCustomerListService;
import com.messaging.ads.meta.dto.CreateMetaAudienceRequest;
import com.messaging.ads.meta.dto.MetaCustomerListMembersRequest;
import com.messaging.ads.optimization.dto.FirstPartyAudienceBuildRequest;
import com.messaging.ads.optimization.dto.FirstPartyAudienceBuildResponse;
import com.messaging.ads.optimization.dto.FirstPartyAudienceBuildResponse.TargetResult;
import com.messaging.common.exception.BadRequestException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FirstPartyAudienceBuilderService {

  private final GoogleAdsAudienceMutationService googleAudienceMutationService;
  private final GoogleDataManagerService googleDataManagerService;
  private final MetaAdsAudienceMutationService metaAudienceMutationService;
  private final MetaAdsCustomerListService metaCustomerListService;

  public FirstPartyAudienceBuildResponse build(
      Long companyId,
      FirstPartyAudienceBuildRequest request,
      Long actorUserId) {
    int membershipDays = request.membershipDays() == null ? 30 : request.membershipDays();
    if (membershipDays < 1 || membershipDays > 540) {
      throw new BadRequestException(
          "First-party audience membership must be between 1 and 540 days");
    }
    validateMembers(request.members());

    List<TargetResult> results = new ArrayList<>();
    for (var target : request.targets().stream().distinct().toList()) {
      try {
        results.add(
            target.provider() == AdProvider.GOOGLE_ADS
                ? buildGoogle(companyId, target.adAccountId(), request, membershipDays, actorUserId)
                : buildMeta(companyId, target.adAccountId(), request, actorUserId));
      } catch (RuntimeException exception) {
        results.add(
            new TargetResult(
                target.provider(),
                target.adAccountId().toString(),
                false,
                null,
                null,
                0,
                0,
                List.of(),
                safeMessage(exception)));
      }
    }
    return new FirstPartyAudienceBuildResponse(
        request.members().size(), List.copyOf(results));
  }

  private TargetResult buildGoogle(
      Long companyId,
      Long accountId,
      FirstPartyAudienceBuildRequest request,
      int membershipDays,
      Long actorUserId) {
    if (!request.googleCustomerMatchTermsAccepted()) {
      throw new BadRequestException(
          "Google Customer Match terms must be accepted for Google targets");
    }

    AdAudienceResponse audience =
        googleAudienceMutationService.create(
            companyId,
            accountId,
            new CreateGoogleAudienceRequest(
                request.name(),
                request.description(),
                membershipDays,
                "CONTACT_INFO"),
            actorUserId);

    List<GoogleCustomerMatchMember> members =
        request.members().stream()
            .map(
                member -> {
                  boolean hasContact = hasText(member.email()) || hasText(member.phone());
                  return new GoogleCustomerMatchMember(
                      hasText(member.email()) ? List.of(member.email()) : List.of(),
                      hasText(member.phone()) ? List.of(member.phone()) : List.of(),
                      List.of(),
                      !hasContact && hasText(member.externalId()) ? member.externalId() : null);
                })
            .toList();

    var upload =
        googleDataManagerService.uploadAudienceMembers(
            companyId,
            Long.valueOf(audience.id()),
            new GoogleCustomerMatchUploadRequest(
                members,
                request.adUserDataConsent(),
                request.adPersonalizationConsent(),
                true,
                false),
            actorUserId);

    return new TargetResult(
        AdProvider.GOOGLE_ADS,
        accountId.toString(),
        true,
        audience.id(),
        audience.externalAudienceId(),
        upload.submittedCount(),
        0,
        upload.warnings(),
        null);
  }

  private TargetResult buildMeta(
      Long companyId,
      Long accountId,
      FirstPartyAudienceBuildRequest request,
      Long actorUserId) {
    if (!request.metaCustomAudienceTermsAccepted()) {
      throw new BadRequestException(
          "Meta Custom Audience terms and first-party authorization must be accepted for Meta targets");
    }

    AdAudienceResponse audience =
        metaAudienceMutationService.create(
            companyId,
            accountId,
            new CreateMetaAudienceRequest(
                request.name(),
                "CUSTOMER_LIST",
                request.description(),
                null,
                null,
                null,
                null,
                null,
                null,
                "USER_PROVIDED_ONLY"),
            actorUserId);

    List<List<String>> rows =
        request.members().stream()
            .map(
                member ->
                    List.of(
                        value(member.email()),
                        value(member.phone()),
                        value(member.externalId())))
            .toList();

    var upload =
        metaCustomerListService.mutateMembers(
            companyId,
            Long.valueOf(audience.id()),
            new MetaCustomerListMembersRequest(
                List.of("EMAIL", "PHONE", "EXTERN_ID"),
                rows,
                MetaCustomerListMembersRequest.Operation.ADD,
                false,
                true),
            actorUserId);

    return new TargetResult(
        AdProvider.META_ADS,
        accountId.toString(),
        true,
        audience.id(),
        audience.externalAudienceId(),
        upload.providerReceivedRows(),
        upload.providerInvalidRows(),
        List.of(),
        null);
  }

  private void validateMembers(List<FirstPartyAudienceBuildRequest.Member> members) {
    for (var member : members) {
      if (member == null
          || (!hasText(member.email())
              && !hasText(member.phone())
              && !hasText(member.externalId()))) {
        throw new BadRequestException(
            "Every first-party audience member needs an email, phone, or external ID");
      }
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String value(String value) {
    return hasText(value) ? value.trim() : "";
  }

  private String safeMessage(RuntimeException exception) {
    // Never reflect provider exception text here: provider validation failures can contain
    // normalized or submitted identifier fragments. The per-target success/counts are enough
    // for this privacy-sensitive fan-out response.
    return "Provider audience build failed. Review the provider connection, eligibility, consent, and audience requirements.";
  }
}
