package com.messaging.ads.capability;

import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.google.GoogleAdsProperties;
import com.messaging.ads.meta.MetaAdsProperties;
import com.messaging.ads.provider.AdsProviderRegistry;
import com.messaging.ads.repository.AdAccountRepository;
import com.messaging.common.exception.NotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdFeatureCapabilityService {

  private static final Set<String> GOOGLE_ADS_SCOPE =
      Set.of("https://www.googleapis.com/auth/adwords");
  private static final Set<String> GOOGLE_DATA_MANAGER_SCOPES =
      Set.of(
          "https://www.googleapis.com/auth/adwords",
          "https://www.googleapis.com/auth/datamanager");
  private static final Set<String> GOOGLE_MERCHANT_SCOPES =
      Set.of(
          "https://www.googleapis.com/auth/adwords",
          "https://www.googleapis.com/auth/content");
  private static final Set<String> META_ADS_SCOPE = Set.of("ads_management");
  private static final Set<String> META_BUSINESS_SCOPE =
      Set.of("ads_management", "business_management");
  private static final Set<String> META_IDENTITY_SCOPES =
      Set.of(
          "ads_management",
          "business_management",
          "pages_show_list",
          "pages_read_engagement",
          "instagram_basic");

  private final AdsProviderRegistry providerRegistry;
  private final AdAccountRepository accountRepository;
  private final GoogleAdsProperties googleProperties;
  private final MetaAdsProperties metaProperties;

  public List<AdFeatureCapabilityResponse> providerCapabilities(AdProvider provider) {
    boolean providerConfigured = providerRegistry.isConfigured(provider);
    return catalog(provider).stream()
        .map(spec -> providerResponse(provider, providerConfigured, spec))
        .sorted(Comparator.comparing(response -> response.feature().name()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<AdFeatureCapabilityResponse> accountCapabilities(Long companyId, Long accountId) {
    AdAccount account =
        accountRepository
            .findByIdAndCompanyId(accountId, companyId)
            .orElseThrow(() -> new NotFoundException("Advertising account not found"));
    boolean providerConfigured = providerRegistry.isConfigured(account.getProvider());
    return catalog(account.getProvider()).stream()
        .map(spec -> accountResponse(account, providerConfigured, spec))
        .sorted(Comparator.comparing(response -> response.feature().name()))
        .toList();
  }

  public AdFeatureCapabilityResponse providerCapability(
      AdProvider provider, AdFeatureCapability feature) {
    return providerCapabilities(provider).stream()
        .filter(response -> response.feature() == feature)
        .findFirst()
        .orElse(
            new AdFeatureCapabilityResponse(
                feature,
                AdFeatureAvailability.UNAVAILABLE,
                false,
                false,
                null,
                true,
                "Feature is not part of this provider capability catalog.",
                apiVersion(provider),
                Set.of()));
  }

  @Transactional(readOnly = true)
  public AdFeatureCapabilityResponse accountCapability(
      Long companyId, Long accountId, AdFeatureCapability feature) {
    return accountCapabilities(companyId, accountId).stream()
        .filter(response -> response.feature() == feature)
        .findFirst()
        .orElse(
            new AdFeatureCapabilityResponse(
                feature,
                AdFeatureAvailability.UNAVAILABLE,
                false,
                false,
                false,
                true,
                "Feature is not part of this provider capability catalog.",
                null,
                Set.of()));
  }

  private AdFeatureCapabilityResponse providerResponse(
      AdProvider provider, boolean providerConfigured, CapabilitySpec spec) {
    boolean infrastructureReady = providerInfrastructureReady(provider, spec);
    boolean implemented = isImplemented(spec.availability());
    boolean supported = providerConfigured && infrastructureReady && implemented;
    String reason =
        unsupportedReason(provider, providerConfigured, infrastructureReady, spec, null, null);
    return new AdFeatureCapabilityResponse(
        spec.feature(),
        spec.availability(),
        implemented,
        supported,
        null,
        spec.requiresManagedAccount(),
        reason,
        apiVersion(provider),
        spec.requiredScopes());
  }

  private AdFeatureCapabilityResponse accountResponse(
      AdAccount account, boolean providerConfigured, CapabilitySpec spec) {
    boolean infrastructureReady = providerInfrastructureReady(account.getProvider(), spec);
    boolean implemented = isImplemented(spec.availability());
    Boolean accountEligible = resolveAccountEligibility(account, spec, providerConfigured, infrastructureReady);
    boolean supported =
        providerConfigured
            && infrastructureReady
            && implemented
            && !Boolean.FALSE.equals(accountEligible);
    String reason =
        unsupportedReason(
            account.getProvider(),
            providerConfigured,
            infrastructureReady,
            spec,
            account,
            accountEligible);
    return new AdFeatureCapabilityResponse(
        spec.feature(),
        spec.availability(),
        implemented,
        supported,
        accountEligible,
        spec.requiresManagedAccount(),
        reason,
        apiVersion(account.getProvider()),
        spec.requiredScopes());
  }

  private Boolean resolveAccountEligibility(
      AdAccount account,
      CapabilitySpec spec,
      boolean providerConfigured,
      boolean infrastructureReady) {
    if (!providerConfigured || !infrastructureReady || !isImplemented(spec.availability())) {
      return false;
    }
    if (!account.isAccessible()) return false;
    if (spec.requiresManagedAccount() && !account.isManaged()) return false;
    if (spec.availability() == AdFeatureAvailability.ACCOUNT_ELIGIBILITY_REQUIRED) {
      return null;
    }
    return true;
  }

  private String unsupportedReason(
      AdProvider provider,
      boolean providerConfigured,
      boolean infrastructureReady,
      CapabilitySpec spec,
      AdAccount account,
      Boolean accountEligible) {
    if (!providerConfigured) return provider.displayName() + " is not configured.";
    if (spec.availability() == AdFeatureAvailability.PLANNED) return spec.reason();
    if (!infrastructureReady && provider == AdProvider.GOOGLE_ADS) {
      return "Google Ads developer token is not configured.";
    }
    if (spec.availability() == AdFeatureAvailability.PROVIDER_HOSTED) return spec.reason();
    if (spec.availability() == AdFeatureAvailability.UNAVAILABLE) return spec.reason();
    if (account != null && !account.isAccessible()) {
      return "Advertising account is no longer accessible.";
    }
    if (account != null && spec.requiresManagedAccount() && !account.isManaged()) {
      return "Advertising account is not enabled for management in Reachlyo.";
    }
    if (spec.availability() == AdFeatureAvailability.ACCOUNT_ELIGIBILITY_REQUIRED
        && accountEligible == null) {
      return spec.reason() == null
          ? "Provider/account eligibility must be verified when the feature is used."
          : spec.reason();
    }
    return spec.reason();
  }

  private boolean providerInfrastructureReady(AdProvider provider, CapabilitySpec spec) {
    if (provider == AdProvider.GOOGLE_ADS && spec.requiresApiAccess()) {
      return googleProperties.hasDeveloperToken();
    }
    return true;
  }

  private boolean isImplemented(AdFeatureAvailability availability) {
    return availability == AdFeatureAvailability.AVAILABLE
        || availability == AdFeatureAvailability.ACCOUNT_ELIGIBILITY_REQUIRED
        || availability == AdFeatureAvailability.READ_ONLY;
  }

  private String apiVersion(AdProvider provider) {
    return provider == AdProvider.GOOGLE_ADS
        ? googleProperties.getApiVersion()
        : metaProperties.getGraphApiVersion();
  }

  private List<CapabilitySpec> catalog(AdProvider provider) {
    return provider == AdProvider.GOOGLE_ADS ? googleCatalog() : metaCatalog();
  }

  private List<CapabilitySpec> googleCatalog() {
    List<CapabilitySpec> specs = new ArrayList<>();
    add(specs, AdFeatureCapability.ACCOUNT_DISCOVERY, false, GOOGLE_ADS_SCOPE);
    add(specs, AdFeatureCapability.CAMPAIGN_CREATE, true, GOOGLE_ADS_SCOPE);
    add(specs, AdFeatureCapability.CAMPAIGN_UPDATE, true, GOOGLE_ADS_SCOPE);
    add(specs, AdFeatureCapability.CAMPAIGN_STATUS, true, GOOGLE_ADS_SCOPE);
    add(specs, AdFeatureCapability.CREATIVE_CREATE, true, GOOGLE_ADS_SCOPE);
    add(specs, AdFeatureCapability.AUDIENCE_CREATE, true, GOOGLE_ADS_SCOPE);
    add(specs, AdFeatureCapability.CONVERSION_CREATE, true, GOOGLE_ADS_SCOPE);
    add(specs, AdFeatureCapability.ANALYTICS_READ, false, GOOGLE_ADS_SCOPE);
    add(specs, AdFeatureCapability.RECOMMENDATIONS, false, GOOGLE_ADS_SCOPE);
    add(specs, AdFeatureCapability.BILLING_READ, false, GOOGLE_ADS_SCOPE);

    add(specs, AdFeatureCapability.GOOGLE_SEARCH, true, GOOGLE_ADS_SCOPE);
    add(specs, AdFeatureCapability.GOOGLE_PERFORMANCE_MAX, true, GOOGLE_ADS_SCOPE);
    add(specs, AdFeatureCapability.GOOGLE_DISPLAY, true, GOOGLE_ADS_SCOPE);
    add(specs, AdFeatureCapability.GOOGLE_DEMAND_GEN, true, GOOGLE_ADS_SCOPE);
    add(specs, AdFeatureCapability.GOOGLE_SHOPPING, true, GOOGLE_MERCHANT_SCOPES);
    specs.add(
        new CapabilitySpec(
            AdFeatureCapability.GOOGLE_STANDALONE_VIDEO_READ_ONLY,
            AdFeatureAvailability.READ_ONLY,
            false,
            true,
            "Standalone Google Video campaigns are synchronized/reported but are not created by Reachlyo.",
            GOOGLE_ADS_SCOPE));
    specs.add(
        new CapabilitySpec(
            AdFeatureCapability.GOOGLE_CUSTOMER_MATCH,
            AdFeatureAvailability.ACCOUNT_ELIGIBILITY_REQUIRED,
            true,
            true,
            "Google Customer Match eligibility/terms must be verified for the selected account.",
            GOOGLE_DATA_MANAGER_SCOPES));
    add(specs, AdFeatureCapability.GOOGLE_DATA_MANAGER, true, GOOGLE_DATA_MANAGER_SCOPES);
    add(specs, AdFeatureCapability.GOOGLE_MERCHANT, true, GOOGLE_MERCHANT_SCOPES);
    add(specs, AdFeatureCapability.GOOGLE_OFFLINE_CONVERSION_UPLOAD, true, GOOGLE_ADS_SCOPE);
    add(specs, AdFeatureCapability.GOOGLE_NATIVE_RECOMMENDATIONS, false, GOOGLE_ADS_SCOPE);

    planned(specs, AdFeatureCapability.GOOGLE_AI_MAX);
    planned(specs, AdFeatureCapability.GOOGLE_APP_CAMPAIGN);
    planned(specs, AdFeatureCapability.GOOGLE_KEYWORD_PLANNER);
    planned(specs, AdFeatureCapability.GOOGLE_FORECAST);
    planned(specs, AdFeatureCapability.GOOGLE_EXPERIMENTS);
    planned(specs, AdFeatureCapability.GOOGLE_ASSET_GENERATION);
    planned(specs, AdFeatureCapability.GOOGLE_BID_SIMULATION);
    specs.add(
        new CapabilitySpec(
            AdFeatureCapability.GOOGLE_BILLING_WRITE_MONTHLY_INVOICE,
            AdFeatureAvailability.PLANNED,
            true,
            true,
            "Reachlyo has not implemented monthly-invoicing billing mutations yet; eligibility is account dependent.",
            GOOGLE_ADS_SCOPE));
    return specs;
  }

  private List<CapabilitySpec> metaCatalog() {
    List<CapabilitySpec> specs = new ArrayList<>();
    add(specs, AdFeatureCapability.ACCOUNT_DISCOVERY, false, META_BUSINESS_SCOPE);
    add(specs, AdFeatureCapability.CAMPAIGN_CREATE, true, META_ADS_SCOPE);
    add(specs, AdFeatureCapability.CAMPAIGN_UPDATE, true, META_ADS_SCOPE);
    add(specs, AdFeatureCapability.CAMPAIGN_STATUS, true, META_ADS_SCOPE);
    add(specs, AdFeatureCapability.CREATIVE_CREATE, true, META_IDENTITY_SCOPES);
    add(specs, AdFeatureCapability.AUDIENCE_CREATE, true, META_ADS_SCOPE);
    add(specs, AdFeatureCapability.CONVERSION_CREATE, true, META_ADS_SCOPE);
    add(specs, AdFeatureCapability.ANALYTICS_READ, false, META_ADS_SCOPE);
    add(specs, AdFeatureCapability.RECOMMENDATIONS, false, META_ADS_SCOPE);
    add(specs, AdFeatureCapability.BILLING_READ, false, META_ADS_SCOPE);

    add(specs, AdFeatureCapability.META_CAMPAIGN_HIERARCHY, true, META_ADS_SCOPE);
    add(specs, AdFeatureCapability.META_IMAGE_VIDEO_CAROUSEL, true, META_IDENTITY_SCOPES);
    add(specs, AdFeatureCapability.META_WEBSITE_AUDIENCE, true, META_ADS_SCOPE);
    add(specs, AdFeatureCapability.META_LOOKALIKE, true, META_ADS_SCOPE);
    add(specs, AdFeatureCapability.META_PIXEL, true, META_ADS_SCOPE);
    add(specs, AdFeatureCapability.META_CUSTOM_CONVERSION, true, META_ADS_SCOPE);
    add(specs, AdFeatureCapability.META_BILLING_SPEND_CAP_WRITE, true, META_ADS_SCOPE);

    planned(specs, AdFeatureCapability.META_DETAILED_TARGETING);
    planned(specs, AdFeatureCapability.META_CUSTOM_AUDIENCE_CUSTOMER_LIST);
    planned(specs, AdFeatureCapability.META_ADVANTAGE_AUDIENCE);
    add(specs, AdFeatureCapability.META_PLACEMENT_TREE, true, META_ADS_SCOPE);
    planned(specs, AdFeatureCapability.META_ADVANTAGE_PLACEMENTS);
    planned(specs, AdFeatureCapability.META_ADVANTAGE_CREATIVE);
    planned(specs, AdFeatureCapability.META_GENERATIVE_TEXT);
    planned(specs, AdFeatureCapability.META_IMAGE_UNCROP);
    planned(specs, AdFeatureCapability.META_BACKGROUND_GENERATION);
    planned(specs, AdFeatureCapability.META_CATALOG);
    planned(specs, AdFeatureCapability.META_EXISTING_POST);
    planned(specs, AdFeatureCapability.META_PARTNERSHIP_ADS);
    specs.add(
        new CapabilitySpec(
            AdFeatureCapability.META_WHATSAPP_STATUS,
            AdFeatureAvailability.PLANNED,
            true,
            true,
            "Reachlyo has not implemented WhatsApp Status ad placement yet; provider account eligibility also applies.",
            META_ADS_SCOPE));
    return specs;
  }

  private void add(
      List<CapabilitySpec> specs,
      AdFeatureCapability feature,
      boolean requiresManagedAccount,
      Set<String> scopes) {
    specs.add(
        new CapabilitySpec(
            feature,
            AdFeatureAvailability.AVAILABLE,
            requiresManagedAccount,
            true,
            null,
            scopes));
  }

  private void planned(List<CapabilitySpec> specs, AdFeatureCapability feature) {
    specs.add(
        new CapabilitySpec(
            feature,
            AdFeatureAvailability.PLANNED,
            true,
            true,
            "Feature is planned but not implemented in Reachlyo yet.",
            Set.of()));
  }

  private record CapabilitySpec(
      AdFeatureCapability feature,
      AdFeatureAvailability availability,
      boolean requiresManagedAccount,
      boolean requiresApiAccess,
      String reason,
      Set<String> requiredScopes) {}
}
