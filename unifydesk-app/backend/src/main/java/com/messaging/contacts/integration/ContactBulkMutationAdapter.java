package com.messaging.contacts.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.contacts.core.domain.ContactTypes.MembershipSource;
import com.messaging.contacts.core.repository.ContactListRepository;
import com.messaging.contacts.core.repository.ContactRepository;
import com.messaging.contacts.core.repository.CustomFieldRepository;
import com.messaging.contacts.core.repository.TagRepository;
import com.messaging.contacts.core.service.ContactService;
import com.messaging.contacts.core.service.CustomFieldValueCodec;
import com.messaging.contacts.processing.ContactBulkMutationPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
public class ContactBulkMutationAdapter implements ContactBulkMutationPort {

  private final ContactRepository contactRepository;
  private final TagRepository tagRepository;
  private final ContactListRepository listRepository;
  private final CustomFieldRepository customFieldRepository;
  private final CustomFieldValueCodec customFieldValueCodec;
  private final ContactService contactService;
  private final ObjectMapper objectMapper;
  private final PlatformTransactionManager transactionManager;

  @Override
  public BatchResult apply(
      long companyId,
      long operationId,
      long requestedByUserId,
      Operation operation,
      List<Long> contactIds) {
    if (operation == null || operation.type() == null || contactIds == null || contactIds.isEmpty()) {
      return new BatchResult(0, 0);
    }

    List<Long> ids = contactIds.stream().distinct().toList();
    if ("DELETE".equals(operation.type())) {
      return deleteIndependently(companyId, requestedByUserId, ids);
    }

    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    BatchResult result =
        transaction.execute(
            status -> {
              if (!contactRepository.allActiveOwned(companyId, ids)) {
                return new BatchResult(0, ids.size());
              }
              return switch (operation.type()) {
                case "ADD_TAG" -> addTag(companyId, requestedByUserId, operation, ids);
                case "REMOVE_TAG" -> removeTag(companyId, operation, ids);
                case "ADD_TO_LIST" ->
                    addToList(companyId, operationId, requestedByUserId, operation, ids);
                case "REMOVE_FROM_LIST" -> removeFromList(companyId, operation, ids);
                case "UPDATE_FIELD" -> updateField(companyId, operation, ids);
                default -> new BatchResult(0, ids.size());
              };
            });
    return result == null ? new BatchResult(0, ids.size()) : result;
  }

  private BatchResult addTag(
      long companyId, long userId, Operation operation, List<Long> ids) {
    Long tagId = operation.targetId();
    if (tagId == null || !tagRepository.allActiveOwned(companyId, Set.of(tagId))) {
      return new BatchResult(0, ids.size());
    }
    tagRepository.addMembershipToContacts(companyId, tagId, ids, userId);
    return new BatchResult(ids.size(), 0);
  }

  private BatchResult removeTag(long companyId, Operation operation, List<Long> ids) {
    Long tagId = operation.targetId();
    if (tagId == null || !tagRepository.allActiveOwned(companyId, Set.of(tagId))) {
      return new BatchResult(0, ids.size());
    }
    tagRepository.removeMembershipFromContacts(companyId, tagId, ids);
    return new BatchResult(ids.size(), 0);
  }

  private BatchResult addToList(
      long companyId,
      long operationId,
      long userId,
      Operation operation,
      List<Long> ids) {
    Long listId = operation.targetId();
    if (listId == null) return new BatchResult(0, ids.size());
    try {
      listRepository.lockActive(companyId, listId);
    } catch (RuntimeException exception) {
      return new BatchResult(0, ids.size());
    }
    listRepository.addMembers(
        companyId,
        listId,
        ids,
        MembershipSource.API,
        "bulk:" + operationId,
        userId);
    return new BatchResult(ids.size(), 0);
  }

  private BatchResult removeFromList(
      long companyId, Operation operation, List<Long> ids) {
    Long listId = operation.targetId();
    if (listId == null) return new BatchResult(0, ids.size());
    try {
      listRepository.lockActive(companyId, listId);
    } catch (RuntimeException exception) {
      return new BatchResult(0, ids.size());
    }
    listRepository.removeMembers(companyId, listId, ids);
    return new BatchResult(ids.size(), 0);
  }

  private BatchResult updateField(
      long companyId, Operation operation, List<Long> ids) {
    Long fieldId = operation.targetId();
    if (fieldId == null || operation.valueJson() == null) {
      return new BatchResult(0, ids.size());
    }
    try {
      var definitions = customFieldRepository.findActiveDefinitions(companyId, Set.of(fieldId));
      JsonNode value = objectMapper.readTree(operation.valueJson());
      CustomFieldValueCodec.Plan plan =
          customFieldValueCodec.validatePrepared(
              definitions, Map.of(Long.toString(fieldId), value));
      customFieldRepository.applyValuesToContacts(
          companyId, ids, plan.deletes(), plan.values());
      return new BatchResult(ids.size(), 0);
    } catch (RuntimeException | java.io.IOException exception) {
      return new BatchResult(0, ids.size());
    }
  }

  /**
   * Contact deletion has its own lifecycle, hooks and optimistic state. Process each Contact in an
   * independent transaction so one worker batch never holds locks across hundreds/thousands of
   * destructive workflows.
   */
  private BatchResult deleteIndependently(long companyId, long userId, List<Long> ids) {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    long succeeded = 0;
    long failed = 0;
    for (Long contactId : ids) {
      try {
        transaction.executeWithoutResult(
            status -> contactService.delete(companyId, contactId, userId));
        succeeded++;
      } catch (RuntimeException exception) {
        failed++;
      }
    }
    return new BatchResult(succeeded, failed);
  }
}
