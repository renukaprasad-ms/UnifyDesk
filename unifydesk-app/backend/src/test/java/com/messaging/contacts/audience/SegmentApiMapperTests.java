package com.messaging.contacts.audience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.TextNode;
import com.messaging.contacts.audience.SegmentApiModel.Field;
import com.messaging.contacts.audience.SegmentApiModel.Group;
import com.messaging.contacts.audience.SegmentApiModel.Predicate;
import com.messaging.contacts.audience.SegmentFieldMetadataRepository.CustomFieldMetadata;
import com.messaging.contacts.audience.SegmentFieldMetadataRepository.CustomFieldType;
import com.messaging.contacts.audience.SegmentFilter.FieldKind;
import com.messaging.contacts.audience.SegmentFilter.GroupNode;
import com.messaging.contacts.audience.SegmentFilter.GroupOperator;
import com.messaging.contacts.audience.SegmentFilter.NoValue;
import com.messaging.contacts.audience.SegmentFilter.PredicateNode;
import com.messaging.contacts.audience.SegmentFilter.SegmentOperator;
import com.messaging.contacts.audience.SegmentFilter.TextValue;
import java.util.List;
import org.junit.jupiter.api.Test;

class SegmentApiMapperTests {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ContactAudienceProperties properties = new ContactAudienceProperties();
  private final SegmentFieldMetadataRepository metadata =
      mock(SegmentFieldMetadataRepository.class);
  private final SegmentApiMapper mapper =
      new SegmentApiMapper(objectMapper, properties, metadata);

  @Test
  void mapsFrozenRawAstWithoutExposingInternalValueWrappers() {
    Group filter =
        new Group(
            GroupOperator.AND,
            List.of(
                new Predicate(
                    new Field(FieldKind.BUILTIN_CONTACT, "displayName", null),
                    SegmentOperator.CONTAINS,
                    TextNode.valueOf("acme")),
                new Predicate(
                    new Field(FieldKind.TAG, "tagId", null),
                    SegmentOperator.HAS_TAG,
                    TextNode.valueOf("123"))));

    SegmentFilter domain = mapper.toDomain(44L, 1, filter);
    GroupNode root = assertInstanceOf(GroupNode.class, domain.root());
    PredicateNode text = assertInstanceOf(PredicateNode.class, root.children().get(0));
    PredicateNode tag = assertInstanceOf(PredicateNode.class, root.children().get(1));

    assertEquals("DISPLAY_NAME", text.field().key());
    assertEquals("acme", assertInstanceOf(TextValue.class, text.value()).value());
    assertEquals(123L, tag.field().id());
    assertInstanceOf(NoValue.class, tag.value());

    Group roundTrip = assertInstanceOf(Group.class, mapper.toApi(domain));
    Predicate apiTag = assertInstanceOf(Predicate.class, roundTrip.children().get(1));
    assertEquals("123", apiTag.value().textValue());
  }

  @Test
  void acceptsPredicateAsFrozenRootAst() {
    Predicate filter =
        new Predicate(
            new Field(FieldKind.BUILTIN_CONTACT, "displayName", null),
            SegmentOperator.EQ,
            TextNode.valueOf("Acme"));

    SegmentFilter domain = mapper.toDomain(1L, 1, filter);
    PredicateNode root = assertInstanceOf(PredicateNode.class, domain.root());
    assertEquals("Acme", assertInstanceOf(TextValue.class, root.value()).value());
    assertInstanceOf(Predicate.class, mapper.toApi(domain));
  }

  @Test
  void rejectsNumericJsonIdsAtWireBoundary() {
    Predicate filter =
        new Predicate(
            new Field(FieldKind.TAG, "tagId", null),
            SegmentOperator.HAS_TAG,
            objectMapper.valueToTree(123L));

    assertThrows(RuntimeException.class, () -> mapper.toDomain(1L, 1, filter));
  }

  @Test
  void rejectsUnsupportedFilterVersion() {
    Group filter = new Group(GroupOperator.AND, List.of());
    assertThrows(RuntimeException.class, () -> mapper.toDomain(1L, 2, filter));
  }

  @Test
  void mapsCustomFieldIdFromDecimalString() {
    when(metadata.requireActiveCustomField(9L, 77L))
        .thenReturn(new CustomFieldMetadata(77L, CustomFieldType.TEXT));
    Group filter =
        new Group(
            GroupOperator.AND,
            List.of(
                new Predicate(
                    new Field(FieldKind.CUSTOM_FIELD, "customField", "77"),
                    SegmentOperator.EQ,
                    TextNode.valueOf("gold"))));

    SegmentFilter domain = mapper.toDomain(9L, 1, filter);
    PredicateNode predicate =
        assertInstanceOf(
            PredicateNode.class,
            assertInstanceOf(GroupNode.class, domain.root()).children().getFirst());
    assertEquals(77L, predicate.field().id());
  }
}
