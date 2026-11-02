package com.commerce.imports;

import java.util.ArrayList;
import java.util.List;

/** Minimal RFC 4180 parser (quoted fields, escaped quotes, CR/LF) plus a formula-injection-safe writer. */
public final class Csv {
    private Csv() {}

    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false, any = false;
        if (text.startsWith("﻿")) text = text.substring(1);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else quoted = false;
                } else cell.append(c);
            } else if (c == '"' && cell.length() == 0) {
                quoted = true; any = true;
            } else if (c == ',') {
                row.add(cell.toString()); cell.setLength(0); any = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                if (any || cell.length() > 0) { row.add(cell.toString()); rows.add(row); }
                row = new ArrayList<>(); cell.setLength(0); any = false;
            } else { cell.append(c); any = true; }
        }
        if (any || cell.length() > 0) { row.add(cell.toString()); rows.add(row); }
        return rows;
    }

    /** Quotes a value; neutralises leading = + - @ so spreadsheet apps never execute a cell as a formula. */
    public static String cell(String v) {
        String s = v == null ? "" : v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    public static String line(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) { if (i > 0) sb.append(','); sb.append(cell(cells.get(i))); }
        return sb.append("\r\n").toString();
    }
}
