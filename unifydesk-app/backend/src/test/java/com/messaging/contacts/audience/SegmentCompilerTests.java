package com.messaging.contacts.audience;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.contacts.audience.SegmentFilter.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class SegmentCompilerTests {

  private final ContactAudienceProperties properties = new ContactAudienceProperties();
  private final SegmentFieldMetadataRepository metadata = mock(SegmentFieldMetadataRepository.class);
  private final SegmentCompiler compiler = new SegmentCompiler(new ObjectMapper(), properties, metadata);

  @Test
  void untrustedValueIsAlwaysBound() {
    String attack = "x' OR 1=1 --";
    SegmentFilter filter =
        new SegmentFilter(
            1,
            new PredicateNode(
                new SegmentField(FieldKind.BUILTIN_CONTACT, "DISPLAY_NAME", null),
                SegmentOperator.EQ,
                new TextValue(attack)));

    SegmentCompiler.CompiledSegment compiled = compiler.compile(42L, filter);

    assertFalse(compiled.whereSql().contains(attack));
    assertTrue(compiled.parameters().containsValue(attack));
    assertTrue(compiled.whereSql().contains("c.company_id = :companyId"));
    assertTrue(compiled.whereSql().contains("c.status = 'ACTIVE'"));
  }

  @Test
  void clientCannotChooseColumnName() {
    SegmentFilter filter =
        new SegmentFilter(
            1,
            new PredicateNode(
                new SegmentField(FieldKind.BUILTIN_CONTACT, "display_name); drop table contacts; --", null),
                SegmentOperator.EQ,
                new TextValue("value")));

    assertThrows(RuntimeException.class, () -> compiler.compile(42L, filter));
  }

  @Test
  void depthAndListLimitsAreEnforced() {
    SegmentNode node =
        new PredicateNode(
            new SegmentField(FieldKind.BUILTIN_CONTACT, "SOURCE", null),
            SegmentOperator.IN,
            new TextListValue(List.of("A", "B")));
    for (int i = 0; i < properties.getSegmentMaxDepth(); i++) {
      node = new GroupNode(GroupOperator.AND, List.of(node));
    }
    SegmentFilter tooDeep = new SegmentFilter(1, node);
    assertThrows(RuntimeException.class, () -> compiler.compile(1L, tooDeep));

    properties.setSegmentMaxListValues(1);
    SegmentFilter tooMany =
        new SegmentFilter(
            1,
            new PredicateNode(
                new SegmentField(FieldKind.BUILTIN_CONTACT, "SOURCE", null),
                SegmentOperator.IN,
                new TextListValue(List.of("A", "B"))));
    assertThrows(RuntimeException.class, () -> compiler.compile(1L, tooMany));
  }
  @Test
  void multiSelectUsesGinFriendlyArrayOperators() {
    when(metadata.requireActiveCustomField(42L, 9L))
        .thenReturn(
            new SegmentFieldMetadataRepository.CustomFieldMetadata(
                9L, SegmentFieldMetadataRepository.CustomFieldType.MULTI_SELECT));
    SegmentFilter filter =
        new SegmentFilter(
            1,
            new PredicateNode(
                new SegmentField(FieldKind.CUSTOM_FIELD, null, 9L),
                SegmentOperator.IN,
                new TextListValue(List.of("gold", "silver"))));

    SegmentCompiler.CompiledSegment compiled = compiler.compile(42L, filter);

    assertTrue(compiled.whereSql().contains("text_array_value && ARRAY["));
    assertFalse(compiled.whereSql().contains("gold"));
    assertTrue(compiled.parameters().containsValue("gold"));
    assertTrue(compiled.parameters().containsValue("silver"));
  }

}
