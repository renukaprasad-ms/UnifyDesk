package com.messaging.contacts.core.service;

import static com.messaging.contacts.core.api.ContactDtos.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.core.domain.ContactTypes.ContactSort;
import com.messaging.contacts.core.domain.ContactTypes.CustomFieldType;
import com.messaging.contacts.core.domain.ContactTypes.MembershipSource;
import com.messaging.contacts.core.domain.CoreRows.ContactListRow;
import com.messaging.contacts.core.domain.CoreRows.ContactSummaryRow;
import com.messaging.contacts.core.domain.CoreRows.CustomFieldDefinitionRow;
import com.messaging.contacts.core.domain.CoreRows.TagRow;
import com.messaging.contacts.core.exception.ContactCoreException;
import com.messaging.contacts.core.repository.ContactListRepository;
import com.messaging.contacts.core.repository.ContactRepository;
import com.messaging.contacts.core.repository.CustomFieldRepository;
import com.messaging.contacts.core.repository.TagRepository;
import com.messaging.contacts.core.service.ContactCursorCodec.Decoded;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContactCatalogService {

  private static final int DEFAULT_PAGE_SIZE = 50;
  private static final int MAX_PAGE_SIZE = 200;
  private static final Pattern FIELD_KEY = Pattern.compile("[a-z][a-z0-9_]{0,63}");

  private final TagRepository tagRepository;
  private final CustomFieldRepository customFieldRepository;
  private final ContactListRepository listRepository;
  private final ContactRepository contactRepository;
  private final ContactQuotaGuard quotaGuard;
  private final CustomFieldValueCodec customFieldCodec;
  private final ContactCursorCodec cursorCodec;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public List<TagResponse> tags(long companyId) {
    return tagRepository.list(companyId).stream().map(this::tag).toList();
  }

  @Transactional
  public TagResponse createTag(long companyId, TagRequest request) {
    try {
      return tag(
          tagRepository.insert(
              companyId,
              normalizedDisplayName(request.name(), 120),
              normalizedName(request.name(), 120),
              clean(request.color()),
              json(request.metadata())));
    } catch (DataIntegrityViolationException exception) {
      throw ContactCoreException.invalidRequest();
    }
  }

  @Transactional
  public TagResponse patchTag(long companyId, long tagId, TagRequest request) {
    try {
      tagRepository.update(
          companyId,
          tagId,
          normalizedDisplayName(request.name(), 120),
          normalizedName(request.name(), 120),
          clean(request.color()),
          json(request.metadata()));
      return tag(tagRepository.require(companyId, tagId));
    } catch (java.util.NoSuchElementException exception) {
      throw ContactCoreException.notFound();
    } catch (DataIntegrityViolationException exception) {
      throw ContactCoreException.invalidRequest();
    }
  }

  @Transactional
  public void archiveTag(long companyId, long tagId) {
    try {
      tagRepository.archive(companyId, tagId);
    } catch (java.util.NoSuchElementException exception) {
      throw ContactCoreException.notFound();
    }
  }

  @Transactional(readOnly = true)
  public List<CustomFieldDefinitionResponse> customFields(long companyId) {
    return customFieldRepository.listDefinitions(companyId).stream()
        .map(this::definition)
        .toList();
  }

  @Transactional
  public CustomFieldDefinitionResponse createCustomField(
      long companyId, CustomFieldDefinitionRequest request) {
    quotaGuard.requireCustomFieldCapacity(companyId);
    String fieldKey = request.fieldKey().trim().toLowerCase(Locale.ROOT);
    if (!FIELD_KEY.matcher(fieldKey).matches()) {
      throw ContactCoreException.invalidField();
    }
    String label = normalizedDisplayName(request.label(), 120);
    CustomFieldType domainType = toDomainType(request.type());
    String options = customFieldCodec.validateOptions(domainType, request.options());
    try {
      return definition(
          customFieldRepository.insertDefinition(
              companyId,
              fieldKey,
              label,
              normalizedName(label, 120),
              domainType,
              options,
              request.position() == null ? 0 : request.position()));
    } catch (DataIntegrityViolationException exception) {
      throw ContactCoreException.invalidField();
    }
  }

  @Transactional
  public CustomFieldDefinitionResponse patchCustomField(
      long companyId, long fieldId, CustomFieldDefinitionPatchRequest request) {
    CustomFieldDefinitionRow current;
    try {
      current = customFieldRepository.requireDefinition(companyId, fieldId);
    } catch (java.util.NoSuchElementException exception) {
      throw ContactCoreException.notFound();
    }
    if (current.status().name().equals("ARCHIVED")) {
      throw ContactCoreException.invalidField();
    }
    String label = normalizedDisplayName(request.label(), 120);
    String options =
        request.options() == null
            ? current.optionsJson()
            : customFieldCodec.validateOptions(current.type(), request.options());
    try {
      customFieldRepository.updateDefinition(
          companyId,
          fieldId,
          label,
          normalizedName(label, 120),
          options,
          request.position() == null ? current.position() : request.position());
      return definition(customFieldRepository.requireDefinition(companyId, fieldId));
    } catch (DataIntegrityViolationException exception) {
      throw ContactCoreException.invalidField();
    }
  }

  @Transactional
  public void archiveCustomField(long companyId, long fieldId) {
    try {
      customFieldRepository.archiveDefinition(companyId, fieldId);
    } catch (java.util.NoSuchElementException exception) {
      throw ContactCoreException.notFound();
    }
  }

  @Transactional(readOnly = true)
  public List<ContactListResponse> lists(long companyId) {
    return listRepository.list(companyId).stream().map(this::list).toList();
  }

  @Transactional(readOnly = true)
  public ContactListResponse getList(long companyId, long listId) {
    try {
      return list(listRepository.require(companyId, listId));
    } catch (java.util.NoSuchElementException exception) {
      throw ContactCoreException.notFound();
    }
  }

  @Transactional
  public ContactListResponse createList(
      long companyId, long userId, ContactListRequest request) {
    quotaGuard.requireListCapacity(companyId);
    String name = normalizedDisplayName(request.name(), 160);
    try {
      return list(
          listRepository.insert(
              companyId,
              userId,
              name,
              normalizedName(name, 160),
              bounded(request.description(), 1000)));
    } catch (DataIntegrityViolationException exception) {
      throw ContactCoreException.invalidRequest();
    }
  }

  @Transactional
  public ContactListResponse patchList(
      long companyId, long listId, ContactListRequest request) {
    String name = normalizedDisplayName(request.name(), 160);
    try {
      listRepository.update(
          companyId,
          listId,
          name,
          normalizedName(name, 160),
          bounded(request.description(), 1000));
      return list(listRepository.require(companyId, listId));
    } catch (java.util.NoSuchElementException exception) {
      throw ContactCoreException.notFound();
    } catch (DataIntegrityViolationException exception) {
      throw ContactCoreException.invalidRequest();
    }
  }

  @Transactional
  public void archiveList(long companyId, long listId) {
    try {
      listRepository.archive(companyId, listId);
    } catch (java.util.NoSuchElementException exception) {
      throw ContactCoreException.notFound();
    }
  }

  @Transactional
  public ContactListResponse addListMembers(
      long companyId, long listId, long userId, AddListMembersRequest request) {
    Set<Long> contactIds = parseIds(request.contactIds());
    try {
      listRepository.lockActive(companyId, listId);
    } catch (java.util.NoSuchElementException exception) {
      throw ContactCoreException.notFound();
    }
    if (!contactRepository.allActiveOwned(companyId, contactIds)) {
      throw ContactCoreException.notFound();
    }
    listRepository.addMembers(
        companyId, listId, contactIds, MembershipSource.MANUAL, null, userId);
    return list(listRepository.require(companyId, listId));
  }

  @Transactional
  public void removeListMember(long companyId, long listId, long contactId) {
    try {
      listRepository.lockActive(companyId, listId);
    } catch (java.util.NoSuchElementException exception) {
      throw ContactCoreException.notFound();
    }
    if (!contactRepository.allActiveOwned(companyId, Set.of(contactId))) {
      throw ContactCoreException.notFound();
    }
    listRepository.removeMember(companyId, listId, contactId);
  }

  @Transactional(readOnly = true)
  public ContactPage<ContactSummary> listMembers(
      long companyId, long listId, String cursor, int requestedSize) {
    ContactListRow list;
    try {
      list = listRepository.require(companyId, listId);
    } catch (java.util.NoSuchElementException exception) {
      throw ContactCoreException.notFound();
    }
    if (list.status().name().equals("ARCHIVED")) {
      throw ContactCoreException.notFound();
    }
    int size =
        requestedSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(MAX_PAGE_SIZE, requestedSize);
    String filterHash = listFilterHash(listId);
    Decoded decoded =
        cursorCodec.decode(cursor, companyId, ContactSort.NAME_ASC, filterHash);
    Long afterId = decoded == null ? null : decoded.contactId();
    List<ContactSummaryRow> rows =
        listRepository.members(companyId, listId, afterId, size + 1);
    boolean hasMore = rows.size() > size;
    List<ContactSummaryRow> visible = hasMore ? rows.subList(0, size) : rows;
    List<ContactSummary> items =
        visible.stream()
            .map(
                row ->
                    new ContactSummary(
                        Long.toString(row.contact().id()),
                        row.contact().displayName(),
                        row.contact().firstName(),
                        row.contact().lastName(),
                        row.contact().organization(),
                        row.primaryEmail(),
                        row.primaryPhone(),
                        Map.of(
                            Channel.EMAIL, row.emailMarketingPreference(),
                            Channel.SMS, row.smsMarketingPreference(),
                            Channel.WHATSAPP, row.whatsappMarketingPreference()),
                        row.contact().status(),
                        row.contact().updatedAt(),
                        row.contact().version()))
            .toList();
    String nextCursor =
        hasMore && !visible.isEmpty()
            ? cursorCodec.encode(
                companyId,
                ContactSort.NAME_ASC,
                filterHash,
                Long.toString(listId),
                visible.getLast().contact().id())
            : null;
    return new ContactPage<>(items, nextCursor, hasMore);
  }

  @Transactional
  public long reconcileListCount(long companyId, long listId) {
    try {
      return listRepository.reconcileMemberCount(companyId, listId);
    } catch (java.util.NoSuchElementException exception) {
      throw ContactCoreException.notFound();
    }
  }

  private Set<Long> parseIds(List<String> values) {
    if (values == null || values.isEmpty() || values.size() > 500) {
      throw ContactCoreException.invalidRequest();
    }
    Set<Long> result = new LinkedHashSet<>();
    for (String value : values) {
      try {
        long id = Long.parseLong(value);
        if (id <= 0) throw ContactCoreException.invalidRequest();
        result.add(id);
      } catch (NumberFormatException exception) {
        throw ContactCoreException.invalidRequest();
      }
    }
    return Set.copyOf(result);
  }

  private TagResponse tag(TagRow row) {
    return new TagResponse(
        Long.toString(row.id()),
        row.name(),
        row.color(),
        readJson(row.metadataJson()),
        row.createdAt(),
        row.updatedAt());
  }

  private CustomFieldDefinitionResponse definition(CustomFieldDefinitionRow row) {
    return new CustomFieldDefinitionResponse(
        Long.toString(row.id()),
        row.fieldKey(),
        row.label(),
        toApiType(row.type()),
        customFieldCodec.readOptions(row.optionsJson()),
        customFieldCodec.indexability(row.type()),
        row.status().name(),
        row.position(),
        row.createdAt(),
        row.updatedAt());
  }

  private ContactListResponse list(ContactListRow row) {
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

  private String normalizedDisplayName(String input, int max) {
    String value = clean(input);
    if (value == null || value.length() > max) {
      throw ContactCoreException.invalidRequest();
    }
    return value;
  }

  private String normalizedName(String input, int max) {
    String value =
        Normalizer.normalize(input.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    if (value.isBlank() || value.length() > max) {
      throw ContactCoreException.invalidRequest();
    }
    return value;
  }

  private String bounded(String input, int max) {
    String value = clean(input);
    if (value != null && value.length() > max) {
      throw ContactCoreException.invalidRequest();
    }
    return value;
  }

  private String clean(String input) {
    if (input == null) return null;
    String value = Normalizer.normalize(input.trim(), Normalizer.Form.NFC);
    return value.isEmpty() ? null : value;
  }

  private String json(com.fasterxml.jackson.databind.JsonNode value) {
    if (value == null || value.isNull()) return null;
    try {
      String json = objectMapper.writeValueAsString(value);
      if (json.getBytes(StandardCharsets.UTF_8).length > 8192) {
        throw ContactCoreException.invalidRequest();
      }
      return json;
    } catch (JsonProcessingException exception) {
      throw ContactCoreException.invalidRequest();
    }
  }

  private com.fasterxml.jackson.databind.JsonNode readJson(String value) {
    if (value == null) return null;
    try {
      return objectMapper.readTree(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Invalid stored Contact tag metadata", exception);
    }
  }

  private CustomFieldType toDomainType(CustomFieldApiType type) {
    return switch (type) {
      case TEXT -> CustomFieldType.TEXT;
      case NUMBER -> CustomFieldType.NUMBER;
      case BOOLEAN -> CustomFieldType.BOOLEAN;
      case DATE -> CustomFieldType.DATE;
      case DATETIME -> CustomFieldType.DATETIME;
      case SINGLE_SELECT -> CustomFieldType.SINGLE_SELECT;
      case MULTI_SELECT -> CustomFieldType.MULTI_SELECT;
    };
  }

  private CustomFieldApiType toApiType(CustomFieldType type) {
    return switch (type) {
      case TEXT -> CustomFieldApiType.TEXT;
      case NUMBER -> CustomFieldApiType.NUMBER;
      case BOOLEAN -> CustomFieldApiType.BOOLEAN;
      case DATE -> CustomFieldApiType.DATE;
      case DATETIME -> CustomFieldApiType.DATETIME;
      case SINGLE_SELECT -> CustomFieldApiType.SINGLE_SELECT;
      case MULTI_SELECT -> CustomFieldApiType.MULTI_SELECT;
    };
  }

  private String listFilterHash(long listId) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(("list:" + listId).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }
}
