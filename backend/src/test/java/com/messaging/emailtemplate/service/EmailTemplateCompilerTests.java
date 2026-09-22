package com.messaging.emailtemplate.service;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import com.messaging.emailtemplate.enums.EmailTemplateEditorMode;
import org.junit.jupiter.api.Test;

class EmailTemplateCompilerTests {
  private final EmailTemplateCompiler compiler =
      new EmailTemplateCompiler(new ObjectMapper(), new EmailVariableService());

  @Test
  void compilesBuilder() {
    String json = """
      {"schemaVersion":1,"settings":{"width":600},"blocks":[
        {"type":"HEADING","text":"Hello {{first_name}}"},
        {"type":"BUTTON","text":"Shop","url":"{{offer_url}}"}
      ]}
      """;
    var result = compiler.compile(EmailTemplateEditorMode.BUILDER,"Hi {{first_name}}",null,json,null);
    assertTrue(result.html().contains("Hello {{first_name}}"));
    assertEquals(java.util.List.of("first_name","offer_url"), result.variables());
  }

  @Test
  void rejectsScriptableHtml() {
    assertThrows(BadRequestException.class, () ->
      compiler.compile(EmailTemplateEditorMode.HTML,"Hi",null,null,"<div onclick=\"x()\">Hi</div>"));
  }
}
