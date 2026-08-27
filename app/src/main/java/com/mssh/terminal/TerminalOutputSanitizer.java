package com.mssh.terminal;

public class TerminalOutputSanitizer {
    private String pendingEscape = "";

    public static String sanitize(String value) {
        return new TerminalOutputSanitizer().sanitizeChunk(value);
    }

    public String sanitizeChunk(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (!pendingEscape.isEmpty()) {
            value = pendingEscape + value;
            pendingEscape = "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == 0x1B) {
                int end = skipEscape(value, i);
                if (end >= value.length()) {
                    pendingEscape = value.substring(i);
                    break;
                }
                i = end;
                continue;
            }
            if (c == '\r') {
                continue;
            }
            if (c == '\b') {
                if (out.length() > 0) {
                    out.deleteCharAt(out.length() - 1);
                }
                continue;
            }
            if (c == '\t' || c == '\n' || c >= 0x20) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private int skipEscape(String value, int escIndex) {
        int next = escIndex + 1;
        if (next >= value.length()) {
            return value.length();
        }
        char type = value.charAt(next);
        if (type == '[') {
            return skipCsi(value, next + 1);
        }
        if (type == ']') {
            return skipOsc(value, next + 1);
        }
        if (type == '(' || type == ')' || type == '#' || type == '%' || type == '=' || type == '>') {
            return Math.min(next + 1, value.length() - 1);
        }
        return next;
    }

    private int skipCsi(String value, int index) {
        for (int i = index; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x40 && c <= 0x7E) {
                return i;
            }
        }
        return value.length();
    }

    private int skipOsc(String value, int index) {
        for (int i = index; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == 0x07) {
                return i;
            }
            if (c == 0x1B && i + 1 < value.length() && value.charAt(i + 1) == '\\') {
                return i + 1;
            }
        }
        return value.length();
    }
}
