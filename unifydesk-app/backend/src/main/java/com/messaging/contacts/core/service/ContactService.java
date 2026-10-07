package com.messaging.contacts.core.service;

import static com.messaging.contacts.core.api.ContactDtos.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.company.enums.CompanyMemberStatus;
import com.messaging.company.repository.CompanyMemberRepository;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.core.domain.ContactTypes.ContactSort;
import com.messaging.contacts.core.domain.ContactTypes.ContactStatus;
import com.messaging.contacts.core.domain.ContactTypes.CoreField;
import com.messaging.contacts.core.domain.ContactTypes.IdentityType;
import com.messaging.contacts.core.domain.CoreRows.ContactListRow;
import com.messaging.contacts.core.domain.CoreRows.ContactRow;
import com.messaging.contacts.core.domain.CoreRows.ContactSummaryRow;
import com.messaging.contacts.core.domain.CoreRows.IdentityRow;
import com.messaging.contacts.core.exception.ContactCoreException;
import com.messaging.contacts.core.repository.ContactListRepository;
import com.messaging.contacts.core.repository.ContactRepository;
import com.messaging.contacts.core.repository.ContactRepository.IdentityKey;
import com.messaging.contacts.core.repository.ContactRepository.SearchSpec;
import com.messaging.contacts.core.repository.CustomFieldRepository;
import com.messaging.contacts.core.repository.TagRepository;
import com.messaging.contacts.core.service.ContactCursorCodec.Decoded;
import com.messaging.contacts.core.service.CustomFieldValueCodec.Plan;
import com.messaging.contacts.core.service.IdentityNormalizer.NormalizedIdentity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContactService {

  private static final int DEFAULT_PAGE_SIZE = 50;
  private static final int MAX_PAGE_SIZE = 200;
  private static final int MIN_SEARCH_PREFIX = 2;
  private static final int MAX_SEARCH_PREFIX = 128;

  private final ContactRepository contactRepository;
  private final TagRepository tagRepository;
  private final CustomFieldRepository customFieldRepository;
  private final ContactListRepository listRepository;
  private final CompanyMemberRepository companyMemberRepository;
  private final IdentityNormalizer identityNormalizer;
  private final CustomFieldValueCodec customFieldCodec;
  private final ContactQuotaGuard quotaGuard;
  private final ContactCursorCodec cursorCodec;
  private final ObjectMapper objectMapper;
  private final List<ContactMergeExtension> mergeExtensions;
  private final List<ContactPurgeHook> purgeHooks;

  @Transactional
  public ContactDetail create(long companyId, long userId, CreateContactRequest request) {
    quotaGuard.requireContactCapacity(companyId);
    Profile profile = profile(request);
    validateOwner(companyId, profile.ownerUserId());

    List<PreparedIdentity> identities = prepareIdentities(request.identities(), profile.countryCode());
    ensureIdentityOwnersFree(companyId, identities);
    Set<Long> tagIds = parseIds(request.tagIds(), 100);
    if (!tagRepository.allActiveOwned(companyId, tagIds)) {
      throw ContactCoreException.notFound();
    }
    Plan fieldPlan = customFieldCodec.validate(companyId, request.customFields());

    try {
      ContactRow contact =
          contactRepository.insertContact(
              companyId,
              userId,
              profile.firstName(),
              profile.lastName(),
              profile.displayName(),
              profile.organization(),
              profile.jobTitle(),
              profile.countryCode(),
              profile.timezone(),
              profile.language(),
              profile.source(),
              profile.notes(),
              profile.ownerUserId());

      for (PreparedIdentity prepared : identities) {
        contactRepository.insertIdentity(
            companyId, contact.id(), prepared.identity(), prepared.primary());
      }
      tagRepository.addMemberships(companyId, contact.id(), tagIds, userId);
      customFieldRepository.upsertValues(companyId, contact.id(), fieldPlan.values());
      return detail(companyId, contact.id(), false);
    } catch (DataIntegrityViolationException exception) {
      throw ContactCoreException.identityConflict();
    }
  }

  @Transactional(readOnly = true)
  public ContactDetail get(long companyId, long contactId) {
    return detail(companyId, contactId, false);
  }

  @Transactional(readOnly = true)
  public ContactDashboardSummary dashboardSummary(long companyId) {
    var counts = contactRepository.dashboardCounts(companyId);
    var growth =
        contactRepository.dashboardGrowth(companyId).stream()
            .map(row -> new ContactGrowthPoint(row.day(), row.added()))
            .toList();
    return new ContactDashboardSummary(
        counts.totalContacts(),
        counts.newContacts30d(),
        counts.emailSubscribers(),
        counts.smsSubscribers(),
        counts.whatsappSubscribers(),
        growth);
  }

  @Transactional(readOnly = true)
  public ContactPage<ContactSummary> search(long companyId, SearchRequest request) {
    int size = request.size() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(MAX_PAGE_SIZE, request.size());
    ContactStatus status = request.status() == null ? ContactStatus.ACTIVE : request.status();
    if (status == ContactStatus.DELETED) {
      throw ContactCoreException.invalidRequest();
    }
    ContactSort sort = request.sort() == null ? ContactSort.UPDATED_DESC : request.sort();
    String query = normalizeSearchQuery(request.q());
    String filterHash = filterHash(request, status, sort, query);
    Decoded decoded = cursorCodec.decode(request.cursor(), companyId, sort, filterHash);

    SearchSpec spec =
        new SearchSpec(
            companyId,
            status,
            sort,
            query,
            query,
            request.tagId(),
            request.listId(),
            request.createdAfter(),
            request.createdBefore(),
            decoded == null ? null : decoded.sortValue(),
            decoded == null ? null : decoded.contactId(),
            size + 1);

    List<ContactSummaryRow> rows = contactRepository.search(spec);
    boolean hasMore = rows.size() > size;
    List<ContactSummaryRow> visible = hasMore ? rows.subList(0, size) : rows;
    List<ContactSummary> items = visible.stream().map(this::summary).toList();
    String nextCursor = null;
    if (hasMore && !visible.isEmpty()) {
      ContactRow last = visible.getLast().contact();
      nextCursor =
          cursorCodec.encode(
              companyId, sort, filterHash, sortValue(last, sort), last.id());
    }
    return new ContactPage<>(items, nextCursor, hasMore);
  }

  @Transactional
  public ContactDetail patch(
      long companyId, long contactId, PatchContactRequest request) {
    ContactRow current = requireActive(companyId, contactId);
    if (request.version() == null || current.version() != request.version()) {
      throw ContactCoreException.versionConflict();
    }

    Map<String, Object> columns = new LinkedHashMap<>();
    putText(columns, "first_name", request.firstName(), 160, false);
    putText(columns, "last_name", request.lastName(), 160, false);
    putText(columns, "display_name", request.displayName(), 240, false);
    putText(columns, "organization", request.organization(), 240, false);
    putText(columns, "job_title", request.jobTitle(), 160, false);
    putCountry(columns, request.countryCode());
    putText(columns, "timezone", request.timezone(), 80, false);
    putText(columns, "language", request.language(), 32, false);
    putText(columns, "source", request.source(), 80, false);
    putText(columns, "notes", request.notes(), 4000, false);
    putOwner(companyId, columns, request.ownerUserId());

    Plan fieldPlan = customFieldCodec.validate(companyId, request.customFields());
    int updated =
        contactRepository.patchContact(companyId, contactId, columns, current.version());
    if (updated == 0) {
      throw ContactCoreException.versionConflict();
    }
    customFieldRepository.deleteValues(companyId, contactId, fieldPlan.deletes());
    customFieldRepository.upsertValues(companyId, contactId, fieldPlan.values());
    return detail(companyId, contactId, false);
  }

  @Transactional
  public DeleteResponse delete(long companyId, long contactId, long userId) {
    ContactRow current;
    try {
      current = contactRepository.lock(companyId, contactId);
    } catch (java.util.NoSuchElementException exception) {
      throw ContactCoreException.notFound();
    }
    if (current.status() == ContactStatus.DELETED) {
      return new DeleteResponse(Long.toString(contactId), "DELETION_QUEUED");
    }
    if (current.status() != ContactStatus.ACTIVE) {
      throw ContactCoreException.notFound();
    }

    if (contactRepository.markDeleted(companyId, contactId, current.version()) == 0) {
      throw ContactCoreException.versionConflict();
    }
    contactRepository.disableAllIdentities(companyId, contactId);
    contactRepository.deleteTagMemberships(companyId, contactId);
    listRepository.decrementCounts(
        companyId, contactRepository.deleteListMemberships(companyId, contactId));
    purgeHooks.forEach(hook -> hook.enqueue(companyId, contactId, userId));
    return new DeleteResponse(Long.toString(contactId), "DELETION_QUEUED");
  }

  @Transactional
  public IdentityResponse addIdentity(
      long companyId, long contactId, CreateIdentityRequest request) {
    ContactRow contact = lockActive(companyId, contactId);
    NormalizedIdentity normalized =
        identityNormalizer.normalize(
            request.type(), request.namespace(), request.value(),
            firstNonBlank(request.countryContext(), contact.countryCode()));
    boolean primary = Boolean.TRUE.equals(request.primary());
    if (normalized.type() == IdentityType.EXTERNAL && primary) {
      throw ContactCoreException.invalidIdentity();
    }

    Map<IdentityKey, Long> owners =
        contactRepository.findActiveIdentityOwners(companyId, List.of(normalized));
    Long owner = owners.get(ContactRepository.key(normalized));
    if (owner != null) {
      throw ContactCoreException.identityConflict();
    }

    try {
      IdentityRow row =
          contactRepository.insertIdentity(companyId, contactId, normalized, primary);
      if (primary) {
        row = contactRepository.setPrimary(companyId, contactId, row.id());
      }
      return identity(row);
    } catch (DataIntegrityViolationException exception) {
      throw ContactCoreException.identityConflict();
    }
  }

  @Transactional
  public IdentityResponse setPrimary(long companyId, long contactId, long identityId) {
    lockActive(companyId, contactId);
    try {
      return identity(contactRepository.setPrimary(companyId, contactId, identityId));
    } catch (java.util.NoSuchElementException exception) {
      throw ContactCoreException.notFound();
    } catch (IllegalArgumentException exception) {
      throw ContactCoreException.invalidIdentity();
    } catch (DataIntegrityViolationException exception) {
      throw ContactCoreException.identityConflict();
    }
  }

  @Transactional
  public void deleteIdentity(long companyId, long contactId, long identityId) {
    lockActive(companyId, contactId);
    try {
      contactRepository.disableIdentity(companyId, contactId, identityId);
    } catch (java.util.NoSuchElementException exception) {
      throw ContactCoreException.notFound();
    }
  }

  @Transactional
  public void addTag(long companyId, long contactId, long tagId, long userId) {
    lockActive(companyId, contactId);
    if (!tagRepository.allActiveOwned(companyId, Set.of(tagId))) {
      throw ContactCoreException.notFound();
    }
    tagRepository.addMembership(companyId, contactId, tagId, userId);
  }

  @Transactional
  public void removeTag(long companyId, long contactId, long tagId) {
    lockActive(companyId, contactId);
    if (!tagRepository.allActiveOwned(companyId, Set.of(tagId))) {
      throw ContactCoreException.notFound();
    }
    tagRepository.removeMembership(companyId, contactId, tagId);
  }

  @Transactional
  public MergeResponse merge(long companyId, long userId, MergeRequest request) {
    long sourceId = parseId(request.sourceContactId());
    long targetId = parseId(request.targetContactId());
    if (sourceId == targetId) {
      throw ContactCoreException.invalidRequest();
    }

    List<ContactRow> locked;
    try {
      locked = contactRepository.lockInIdOrder(companyId, sourceId, targetId);
    } catch (java.util.NoSuchElementException exception) {
      throw ContactCoreException.notFound();
    }
    ContactRow source =
        locked.stream().filter(row -> row.id() == sourceId).findFirst().orElseThrow();
    ContactRow target =
        locked.stream().filter(row -> row.id() == targetId).findFirst().orElseThrow();
    if (source.status() != ContactStatus.ACTIVE || target.status() != ContactStatus.ACTIVE) {
      throw ContactCoreException.notFound();
    }
    if (source.version() != request.sourceVersion()
        || target.version() != request.targetVersion()) {
      throw ContactCoreException.versionConflict();
    }

    MergeFieldStrategy strategy =
        request.fieldStrategy() == null
            ? new MergeFieldStrategy(Map.of(), Map.of())
            : request.fieldStrategy();
    Set<Long> sourceWinsFields = validateCustomMergeStrategy(companyId, strategy.customFields());

    Map<String, Object> corePatch = coreMergePatch(source, target, strategy.coreFields());
    if (!corePatch.isEmpty()
        && contactRepository.patchContact(
                companyId, targetId, corePatch, target.version())
            == 0) {
      throw ContactCoreException.versionConflict();
    }

    try {
      contactRepository.moveIdentitiesForMerge(companyId, sourceId, targetId);
      tagRepository.mergeMemberships(companyId, sourceId, targetId);
      listRepository.mergeMemberships(companyId, sourceId, targetId);
      customFieldRepository.mergeValues(companyId, sourceId, targetId, sourceWinsFields);
      for (ContactMergeExtension extension : mergeExtensions) {
        extension.reconcile(companyId, sourceId, targetId, userId);
      }
      contactRepository.finalizeMerge(companyId, sourceId, targetId);
      contactRepository.appendMergeAudit(
          companyId, sourceId, targetId, serializeStrategy(strategy), userId);
    } catch (DataIntegrityViolationException exception) {
      throw ContactCoreException.identityConflict();
    }

    ContactRow mergedTarget = requireActive(companyId, targetId);
    return new MergeResponse(
        Long.toString(sourceId), Long.toString(targetId), mergedTarget.version());
  }

  private ContactDetail detail(long companyId, long contactId, boolean includeDeleted) {
    ContactRow contact =
        contactRepository.find(companyId, contactId).orElseThrow(ContactCoreException::notFound);
    if (contact.status() == ContactStatus.DELETED && !includeDeleted) {
      throw ContactCoreException.notFound();
    }

    List<IdentityResponse> identities =
        contact.status() == ContactStatus.ACTIVE
            ? contactRepository.identities(companyId, contactId).stream()
                .filter(row -> row.status().name().equals("ACTIVE"))
                .map(this::identity)
                .toList()
            : List.of();
    String primaryEmail = preferredIdentity(identities, IdentityType.EMAIL);
    String primaryPhone = preferredIdentity(identities, IdentityType.PHONE);
    List<TagResponse> tags =
        contact.status() == ContactStatus.ACTIVE
            ? tagRepository.tagsForContact(companyId, contactId).stream()
                .map(
                    row ->
                        new TagResponse(
                            Long.toString(row.id()),
                            row.name(),
                            row.color(),
                            readJson(row.metadataJson()),
                            row.createdAt(),
                            row.updatedAt()))
                .toList()
            : List.of();
    List<ContactListResponse> lists =
        contact.status() == ContactStatus.ACTIVE
            ? listRepository.listsForContact(companyId, contactId).stream()
                .map(this::contactList)
                .toList()
            : List.of();
    Map<String, JsonNode> customFields = new LinkedHashMap<>();
    if (contact.status() == ContactStatus.ACTIVE) {
      customFieldRepository
          .values(companyId, contactId)
          .forEach(
              row ->
                  customFields.put(
                      Long.toString(row.fieldId()), customFieldCodec.toJson(row)));
    }

    return new ContactDetail(
        Long.toString(contact.id()),
        Long.toString(contact.companyId()),
        contact.firstName(),
        contact.lastName(),
        contact.displayName(),
        contact.organization(),
        primaryEmail,
        primaryPhone,
        contact.jobTitle(),
        contact.countryCode(),
        contact.timezone(),
        contact.language(),
        contact.source(),
        contact.notes(),
        contact.ownerUserId() == null ? null : Long.toString(contact.ownerUserId()),
        contact.status(),
        contact.mergedIntoContactId() == null
            ? null
            : Long.toString(contact.mergedIntoContactId()),
        identities,
        tags,
        lists,
        Map.copyOf(customFields),
        contact.createdAt(),
        contact.updatedAt(),
        contact.version());
  }

  private ContactSummary summary(ContactSummaryRow row) {
    ContactRow contact = row.contact();
    return new ContactSummary(
        Long.toString(contact.id()),
        contact.displayName(),
        contact.firstName(),
        contact.lastName(),
        contact.organization(),
        row.primaryEmail(),
        row.primaryPhone(),
        Map.of(
            Channel.EMAIL, row.emailMarketingPreference(),
            Channel.SMS, row.smsMarketingPreference(),
            Channel.WHATSAPP, row.whatsappMarketingPreference()),
        contact.status(),
        contact.updatedAt(),
        contact.version());
  }

  private IdentityResponse identity(IdentityRow row) {
    return new IdentityResponse(
        Long.toString(row.id()),
        row.type(),
        row.namespace(),
        row.value(),
        row.normalizedValue(),
        row.primary(),
        row.status().name());
  }

  private String preferredIdentity(List<IdentityResponse> identities, IdentityType type) {
    return identities.stream()
        .filter(identity -> identity.type() == type)
        .map(IdentityResponse::normalizedValue)
        .findFirst()
        .orElse(null);
  }

  private ContactListResponse contactList(ContactListRow row) {
    return new ContactListResponse(
        Long.toString(row.id()),
        row.name(),
        row.description(),
        row.status().name(),
        row.memberCount(),
        row.createdAt(),
        row.updatedAt(),
        row.version());
  }

  private ContactRow requireActive(long companyId, long contactId) {
    ContactRow row =
        contactRepository.find(companyId, contactId).orElseThrow(ContactCoreException::notFound);
    if (row.status() != ContactStatus.ACTIVE) {
      throw ContactCoreException.notFound();
    }
    return row;
  }

  private ContactRow lockActive(long companyId, long contactId) {
    ContactRow row;
    try {
      row = contactRepository.lock(companyId, contactId);
    } catch (java.util.NoSuchElementException exception) {
      throw ContactCoreException.notFound();
    }
    if (row.status() != ContactStatus.ACTIVE) {
      throw ContactCoreException.notFound();
    }
    return row;
  }

  private List<PreparedIdentity> prepareIdentities(
      List<IdentityInput> inputs, String countryContext) {
    if (inputs == null || inputs.isEmpty()) {
      return List.of();
    }
    Map<IdentityKey, PreparedIdentity> unique = new LinkedHashMap<>();
    Set<IdentityType> primaryTypes = new HashSet<>();
    for (IdentityInput input : inputs) {
      NormalizedIdentity normalized =
          identityNormalizer.normalize(
              input.type(),
              input.namespace(),
              input.value(),
              firstNonBlank(input.countryContext(), countryContext));
      boolean primary = Boolean.TRUE.equals(input.primary());
      if (normalized.type() == IdentityType.EXTERNAL && primary) {
        throw ContactCoreException.invalidIdentity();
      }
      if (primary && !primaryTypes.add(normalized.type())) {
        throw ContactCoreException.invalidIdentity();
      }
      PreparedIdentity previous =
          unique.put(
              ContactRepository.key(normalized), new PreparedIdentity(normalized, primary));
      if (previous != null) {
        throw ContactCoreException.identityConflict();
      }
    }
    return List.copyOf(unique.values());
  }

  private void ensureIdentityOwnersFree(long companyId, List<PreparedIdentity> identities) {
    Set<IdentityKey> requested =
        identities.stream()
            .map(PreparedIdentity::identity)
            .map(ContactRepository::key)
            .collect(java.util.stream.Collectors.toSet());
    Map<IdentityKey, Long> owners =
        contactRepository.findActiveIdentityOwners(
            companyId, identities.stream().map(PreparedIdentity::identity).toList());
    if (owners.keySet().stream().anyMatch(requested::contains)) {
      throw ContactCoreException.identityConflict();
    }
  }

  private Set<Long> validateCustomMergeStrategy(
      long companyId, Map<String, MergeWinner> customStrategy) {
    if (customStrategy == null || customStrategy.isEmpty()) {
      return Set.of();
    }
    Set<Long> ids = new LinkedHashSet<>();
    Set<Long> sourceWins = new LinkedHashSet<>();
    for (Map.Entry<String, MergeWinner> entry : customStrategy.entrySet()) {
      long id = parseId(entry.getKey());
      ids.add(id);
      if (entry.getValue() == null) {
        throw ContactCoreException.invalidRequest();
      }
      if (entry.getValue() == MergeWinner.SOURCE) {
        sourceWins.add(id);
      }
    }
    if (customFieldRepository.findActiveDefinitions(companyId, ids).size() != ids.size()) {
      throw ContactCoreException.invalidField();
    }
    return Set.copyOf(sourceWins);
  }

  private Map<String, Object> coreMergePatch(
      ContactRow source, ContactRow target, Map<CoreField, MergeWinner> strategy) {
    EnumMap<CoreField, MergeWinner> choices = new EnumMap<>(CoreField.class);
    if (strategy != null) {
      choices.putAll(strategy);
    }
    Map<String, Object> patch = new LinkedHashMap<>();
    mergeValue(patch, "first_name", source.firstName(), target.firstName(), choices.get(CoreField.FIRST_NAME));
    mergeValue(patch, "last_name", source.lastName(), target.lastName(), choices.get(CoreField.LAST_NAME));
    mergeValue(patch, "display_name", source.displayName(), target.displayName(), choices.get(CoreField.DISPLAY_NAME));
    mergeValue(patch, "organization", source.organization(), target.organization(), choices.get(CoreField.ORGANIZATION));
    mergeValue(patch, "job_title", source.jobTitle(), target.jobTitle(), choices.get(CoreField.JOB_TITLE));
    mergeValue(patch, "country_code", source.countryCode(), target.countryCode(), choices.get(CoreField.COUNTRY_CODE));
    mergeValue(patch, "timezone", source.timezone(), target.timezone(), choices.get(CoreField.TIMEZONE));
    mergeValue(patch, "language", source.language(), target.language(), choices.get(CoreField.LANGUAGE));
    mergeValue(patch, "source", source.source(), target.source(), choices.get(CoreField.SOURCE));
    mergeValue(patch, "notes", source.notes(), target.notes(), choices.get(CoreField.NOTES));
    return patch;
  }

  private void mergeValue(
      Map<String, Object> patch,
      String column,
      Object source,
      Object target,
      MergeWinner winner) {
    Object selected = winner == MergeWinner.SOURCE ? source : (target != null ? target : source);
    if (!Objects.equals(selected, target)) {
      patch.put(column, selected);
    }
  }

  private String serializeStrategy(MergeFieldStrategy strategy) {
    try {
      return objectMapper.writeValueAsString(strategy);
    } catch (JsonProcessingException exception) {
      throw ContactCoreException.invalidRequest();
    }
  }

  private Profile profile(CreateContactRequest request) {
    String country = normalizeCountry(request.countryCode());
    return new Profile(
        clean(request.firstName()),
        clean(request.lastName()),
        clean(request.displayName()),
        clean(request.organization()),
        clean(request.jobTitle()),
        country,
        clean(request.timezone()),
        clean(request.language()),
        clean(request.source()),
        clean(request.notes()),
        parseNullableId(request.ownerUserId()));
  }

  private void validateOwner(long companyId, Long ownerUserId) {
    if (ownerUserId == null) {
      return;
    }
    if (!companyMemberRepository.existsByCompanyIdAndUserIdAndStatus(
        companyId, ownerUserId, CompanyMemberStatus.ACTIVE)) {
      throw ContactCoreException.notFound();
    }
  }

  private void putOwner(long companyId, Map<String, Object> columns, JsonNode node) {
    if (node == null) return;
    if (node.isNull()) {
      columns.put("owner_user_id", null);
      return;
    }
    if (!node.isTextual()) throw ContactCoreException.invalidRequest();
    Long owner = parseNullableId(node.textValue());
    validateOwner(companyId, owner);
    columns.put("owner_user_id", owner);
  }

  private void putCountry(Map<String, Object> columns, JsonNode node) {
    if (node == null) return;
    if (node.isNull()) {
      columns.put("country_code", null);
      return;
    }
    if (!node.isTextual()) throw ContactCoreException.invalidRequest();
    columns.put("country_code", normalizeCountry(node.textValue()));
  }

  private void putText(
      Map<String, Object> columns,
      String column,
      JsonNode node,
      int maxLength,
      boolean required) {
    if (node == null) return;
    if (node.isNull()) {
      if (required) throw ContactCoreException.invalidRequest();
      columns.put(column, null);
      return;
    }
    if (!node.isTextual()) throw ContactCoreException.invalidRequest();
    String value = clean(node.textValue());
    if ((required && value == null) || (value != null && value.length() > maxLength)) {
      throw ContactCoreException.invalidRequest();
    }
    columns.put(column, value);
  }

  private String normalizeSearchQuery(String q) {
    if (q == null || q.isBlank()) return null;
    String value =
        Normalizer.normalize(q.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    if (value.length() < MIN_SEARCH_PREFIX || value.length() > MAX_SEARCH_PREFIX) {
      throw ContactCoreException.invalidRequest();
    }
    return value;
  }

  private String filterHash(
      SearchRequest request, ContactStatus status, ContactSort sort, String query) {
    String value =
        String.join(
            "|",
            status.name(),
            sort.name(),
            query == null ? "" : query,
            request.tagId() == null ? "" : request.tagId().toString(),
            request.listId() == null ? "" : request.listId().toString(),
            request.createdAfter() == null ? "" : request.createdAfter().toString(),
            request.createdBefore() == null ? "" : request.createdBefore().toString());
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  private String sortValue(ContactRow row, ContactSort sort) {
    return switch (sort) {
      case UPDATED_DESC -> row.updatedAt().toString();
      case CREATED_DESC -> row.createdAt().toString();
      case NAME_ASC ->
          row.displayName() == null ? "" : row.displayName().toLowerCase(Locale.ROOT);
    };
  }

  private Set<Long> parseIds(List<String> values, int max) {
    if (values == null || values.isEmpty()) return Set.of();
    if (values.size() > max) throw ContactCoreException.invalidRequest();
    Set<Long> ids = new LinkedHashSet<>();
    for (String value : values) ids.add(parseId(value));
    return Set.copyOf(ids);
  }

  long parseId(String value) {
    try {
      long id = Long.parseLong(value);
      if (id <= 0) throw ContactCoreException.invalidRequest();
      return id;
    } catch (NumberFormatException exception) {
      throw ContactCoreException.invalidRequest();
    }
  }

  private Long parseNullableId(String value) {
    if (value == null || value.isBlank()) return null;
    return parseId(value);
  }

  private String normalizeCountry(String country) {
    String value = clean(country);
    if (value == null) return null;
    value = value.toUpperCase(Locale.ROOT);
    if (!value.matches("[A-Z]{2}")) throw ContactCoreException.invalidRequest();
    return value;
  }

  private String clean(String value) {
    if (value == null) return null;
    String cleaned = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
    return cleaned.isEmpty() ? null : cleaned;
  }

  private String firstNonBlank(String first, String second) {
    return first != null && !first.isBlank() ? first : second;
  }

  private JsonNode readJson(String value) {
    if (value == null) return null;
    try {
      return objectMapper.readTree(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Invalid stored Contact JSON", exception);
    }
  }

  private record PreparedIdentity(NormalizedIdentity identity, boolean primary) {}

  private record Profile(
      String firstName,
      String lastName,
      String displayName,
      String organization,
      String jobTitle,
      String countryCode,
      String timezone,
      String language,
      String source,
      String notes,
      Long ownerUserId) {}
}
