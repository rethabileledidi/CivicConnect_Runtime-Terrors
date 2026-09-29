package com.civicconnect.reporting;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvWriterTest {

    @Test
    void quotesCommasQuotesAndNewlines() {
        assertEquals("\"Main Rd, Mamelodi\"", CsvWriter.escape("Main Rd, Mamelodi"));
        assertEquals("\"He said \"\"urgent\"\"\"", CsvWriter.escape("He said \"urgent\""));
        assertEquals("\"line1\nline2\"", CsvWriter.escape("line1\nline2"));
    }

    @Test
    void neutralisesSpreadsheetFormulas() {
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\"", CsvWriter.escape("=HYPERLINK(\"x\")"));
        assertEquals("'+27 12 345", CsvWriter.escape("+27 12 345"));
        assertEquals("'@SUM(A1)", CsvWriter.escape("@SUM(A1)"));
    }

    @Test
    void numbersAreNotPrefixed() {
        assertEquals("-3.5", CsvWriter.escape(new BigDecimal("-3.5")));
        assertEquals("", CsvWriter.escape(null));
    }
}
