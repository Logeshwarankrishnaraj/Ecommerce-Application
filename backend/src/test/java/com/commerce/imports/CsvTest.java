package com.commerce.imports;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvTest {
    @Test
    void parsesQuotedCommasQuotesAndNewlines() {
        var rows = Csv.parse("a,b\r\n\"x, y\",\"say \"\"hi\"\"\"\n\"line1\nline2\",z\n");
        assertEquals(3, rows.size());
        assertEquals(List.of("x, y", "say \"hi\""), rows.get(1));
        assertEquals("line1\nline2", rows.get(2).get(0));
    }

    @Test
    void stripsBomAndSkipsBlankLines() {
        var rows = Csv.parse("﻿sku,title\n\nA1,One\n");
        assertEquals(List.of("sku", "title"), rows.get(0));
        assertEquals(2, rows.size());
    }

    @Test
    void writerNeutralisesFormulaInjection() {
        assertEquals("\"'=SUM(A1)\"", Csv.cell("=SUM(A1)"));
        assertEquals("\"'@cmd\"", Csv.cell("@cmd"));
        assertEquals("\"plain\"", Csv.cell("plain"));
    }

    @Test
    void unzipRejectsGarbage() {
        assertThrows(com.commerce.common.exception.ValidationFailedException.class,
                () -> BulkImportService.unzip(new byte[] {1, 2, 3}) );
    }
}
