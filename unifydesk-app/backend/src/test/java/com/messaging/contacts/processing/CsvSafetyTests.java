package com.messaging.contacts.processing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CsvSafetyTests {

  @Test
  void neutralizesSpreadsheetFormulaPrefixes() {
    assertEquals("'=1+1", CsvSafety.neutralizeFormula("=1+1"));
    assertEquals("'  @cmd", CsvSafety.neutralizeFormula("  @cmd"));
    assertEquals("normal", CsvSafety.neutralizeFormula("normal"));
  }

  @Test
  void rejectsNulAndOversizedCells() {
    assertThrows(RuntimeException.class, () -> CsvSafety.validateCell("a\u0000b", 100));
    assertThrows(RuntimeException.class, () -> CsvSafety.validateCell("abcd", 3));
  }
}
