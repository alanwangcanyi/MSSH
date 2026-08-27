package com.mssh.terminal;

import java.util.ArrayList;
import java.util.List;

public class TerminalScreenBuffer {
    private static final int DEFAULT_COLUMNS = 80;
    private static final int DEFAULT_ROWS = 32;
    private static final int MAX_SCROLLBACK = 800;

    private final List<String> scrollback = new ArrayList<>();
    private char[][] screen;
    private int columns = DEFAULT_COLUMNS;
    private int rows = DEFAULT_ROWS;
    private int row;
    private int column;
    private int savedRow;
    private int savedColumn;
    private boolean alternateScreen;
    private List<String> primaryScrollback;
    private char[][] primaryScreen;
    private int primaryRow;
    private int primaryColumn;
    private State state = State.NORMAL;
    private final StringBuilder sequence = new StringBuilder();

    public TerminalScreenBuffer() {
        clearScreen();
    }

    public synchronized String append(String value) {
        if (value == null || value.isEmpty()) {
            return render();
        }
        for (int i = 0; i < value.length(); i++) {
            consume(value.charAt(i));
        }
        return render();
    }

    public synchronized void resize(int columns, int rows) {
        if (columns < 20 || rows < 8) {
            return;
        }
        String snapshot = render();
        this.columns = columns;
        this.rows = rows;
        clearScreen();
        restoreSnapshot(snapshot);
    }

    public synchronized boolean isAlternateScreen() {
        return alternateScreen;
    }

    private void consume(char c) {
        switch (state) {
            case ESC:
                consumeEsc(c);
                return;
            case CSI:
                consumeCsi(c);
                return;
            case OSC:
                consumeOsc(c);
                return;
            case OSC_ESC:
                state = c == '\\' ? State.NORMAL : State.OSC;
                return;
            case NORMAL:
            default:
                consumeNormal(c);
        }
    }

    private void consumeNormal(char c) {
        if (c == 0x1B) {
            sequence.setLength(0);
            state = State.ESC;
            return;
        }
        if (c == '\r') {
            column = 0;
            return;
        }
        if (c == '\n') {
            newLine();
            return;
        }
        if (c == '\b') {
            column = Math.max(0, column - 1);
            return;
        }
        if (c == '\t') {
            int spaces = 8 - (column % 8);
            for (int i = 0; i < spaces; i++) {
                putChar(' ');
            }
            return;
        }
        if (c >= 0x20) {
            putChar(c);
        }
    }

    private void consumeEsc(char c) {
        if (c == '[') {
            sequence.setLength(0);
            state = State.CSI;
            return;
        }
        if (c == ']') {
            sequence.setLength(0);
            state = State.OSC;
            return;
        }
        if (c == '7') {
            saveCursor();
        } else if (c == '8') {
            restoreCursor();
        } else if (c == 'c') {
            reset();
        } else if (c == 'D') {
            newLine();
        } else if (c == 'E') {
            column = 0;
            newLine();
        } else if (c == 'M') {
            reverseIndex();
        }
        state = State.NORMAL;
    }

    private void consumeCsi(char c) {
        if (c >= 0x40 && c <= 0x7E) {
            handleCsi(sequence.toString(), c);
            sequence.setLength(0);
            state = State.NORMAL;
            return;
        }
        sequence.append(c);
    }

    private void consumeOsc(char c) {
        if (c == 0x07) {
            state = State.NORMAL;
            sequence.setLength(0);
            return;
        }
        if (c == 0x1B) {
            state = State.OSC_ESC;
            return;
        }
        sequence.append(c);
    }

    private void handleCsi(String params, char command) {
        boolean privateMode = params.startsWith("?");
        String clean = privateMode ? params.substring(1) : params;
        int[] values = parseParams(clean);
        switch (command) {
            case 'A':
                row = clamp(row - first(values, 1), 0, rows - 1);
                break;
            case 'B':
                row = clamp(row + first(values, 1), 0, rows - 1);
                break;
            case 'C':
                column = clamp(column + first(values, 1), 0, columns - 1);
                break;
            case 'D':
                column = clamp(column - first(values, 1), 0, columns - 1);
                break;
            case 'E':
                row = clamp(row + first(values, 1), 0, rows - 1);
                column = 0;
                break;
            case 'F':
                row = clamp(row - first(values, 1), 0, rows - 1);
                column = 0;
                break;
            case 'G':
            case '`':
                column = clamp(first(values, 1) - 1, 0, columns - 1);
                break;
            case 'H':
            case 'f':
                row = clamp(first(values, 1) - 1, 0, rows - 1);
                column = clamp(second(values, 1) - 1, 0, columns - 1);
                break;
            case 'J':
                eraseDisplay(first(values, 0));
                break;
            case 'K':
                eraseLine(first(values, 0));
                break;
            case '@':
                insertChars(first(values, 1));
                break;
            case 'P':
                deleteChars(first(values, 1));
                break;
            case 'X':
                eraseChars(first(values, 1));
                break;
            case 'L':
                insertLines(first(values, 1));
                break;
            case 'M':
                deleteLines(first(values, 1));
                break;
            case 'S':
                scrollUpBy(first(values, 1));
                break;
            case 'T':
                scrollDownBy(first(values, 1));
                break;
            case 'd':
                row = clamp(first(values, 1) - 1, 0, rows - 1);
                break;
            case 'e':
                row = clamp(row + first(values, 1), 0, rows - 1);
                break;
            case 'm':
            case 'n':
            case 'r':
                break;
            case 's':
                saveCursor();
                break;
            case 'u':
                restoreCursor();
                break;
            case 'h':
                handleMode(values, privateMode, true);
                break;
            case 'l':
                handleMode(values, privateMode, false);
                break;
            default:
                break;
        }
    }

    private void handleMode(int[] values, boolean privateMode, boolean enabled) {
        if (!privateMode) {
            return;
        }
        for (int value : values) {
            if (value == 47 || value == 1047 || value == 1049) {
                if (enabled && !alternateScreen) {
                    savePrimaryScreen();
                    alternateScreen = true;
                    clearScreen();
                    row = 0;
                    column = 0;
                } else if (!enabled && alternateScreen) {
                    alternateScreen = false;
                    restorePrimaryScreen();
                }
            }
        }
    }

    private void putChar(char c) {
        screen[row][column] = c;
        column++;
        if (column >= columns) {
            column = 0;
            newLine();
        }
    }

    private void newLine() {
        if (row == rows - 1) {
            scrollUp();
        } else {
            row++;
        }
    }

    private void scrollUp() {
        if (!alternateScreen) {
            addScrollback(trimRight(screen[0]));
        }
        for (int r = 1; r < rows; r++) {
            System.arraycopy(screen[r], 0, screen[r - 1], 0, columns);
        }
        fillLine(rows - 1, 0, columns);
    }

    private void scrollUpBy(int count) {
        int lines = clamp(count, 1, rows);
        for (int i = 0; i < lines; i++) {
            scrollUp();
        }
    }

    private void scrollDownBy(int count) {
        int lines = clamp(count, 1, rows);
        for (int r = rows - 1 - lines; r >= 0; r--) {
            System.arraycopy(screen[r], 0, screen[r + lines], 0, columns);
        }
        for (int r = 0; r < lines; r++) {
            fillLine(r, 0, columns);
        }
    }

    private void reverseIndex() {
        if (row == 0) {
            scrollDownBy(1);
        } else {
            row--;
        }
    }

    private void eraseDisplay(int mode) {
        if (mode == 2 || mode == 3) {
            clearScreen();
            row = 0;
            column = 0;
            return;
        }
        if (mode == 1) {
            for (int r = 0; r < row; r++) {
                fillLine(r, 0, columns);
            }
            fillLine(row, 0, column + 1);
            return;
        }
        fillLine(row, column, columns);
        for (int r = row + 1; r < rows; r++) {
            fillLine(r, 0, columns);
        }
    }

    private void eraseLine(int mode) {
        if (mode == 2) {
            fillLine(row, 0, columns);
        } else if (mode == 1) {
            fillLine(row, 0, column + 1);
        } else {
            fillLine(row, column, columns);
        }
    }

    private void eraseChars(int count) {
        fillLine(row, column, column + clamp(count, 1, columns));
    }

    private void insertChars(int count) {
        int chars = clamp(count, 1, columns - column);
        int moveCount = columns - column - chars;
        if (moveCount > 0) {
            System.arraycopy(screen[row], column, screen[row], column + chars, moveCount);
        }
        fillLine(row, column, column + chars);
    }

    private void deleteChars(int count) {
        int chars = clamp(count, 1, columns - column);
        int moveCount = columns - column - chars;
        if (moveCount > 0) {
            System.arraycopy(screen[row], column + chars, screen[row], column, moveCount);
        }
        fillLine(row, columns - chars, columns);
    }

    private void insertLines(int count) {
        int lines = clamp(count, 1, rows - row);
        for (int r = rows - 1 - lines; r >= row; r--) {
            System.arraycopy(screen[r], 0, screen[r + lines], 0, columns);
        }
        for (int r = row; r < row + lines; r++) {
            fillLine(r, 0, columns);
        }
    }

    private void deleteLines(int count) {
        int lines = clamp(count, 1, rows - row);
        for (int r = row + lines; r < rows; r++) {
            System.arraycopy(screen[r], 0, screen[r - lines], 0, columns);
        }
        for (int r = rows - lines; r < rows; r++) {
            fillLine(r, 0, columns);
        }
    }

    public synchronized void reset() {
        scrollback.clear();
        alternateScreen = false;
        primaryScrollback = null;
        primaryScreen = null;
        row = 0;
        column = 0;
        clearScreen();
    }

    private void restoreSnapshot(String snapshot) {
        scrollback.clear();
        if (snapshot == null || snapshot.isEmpty()) {
            row = 0;
            column = 0;
            return;
        }
        String[] lines = snapshot.split("\n", -1);
        int firstScreenLine = Math.max(0, lines.length - rows);
        if (!alternateScreen) {
            for (int i = 0; i < firstScreenLine; i++) {
                addScrollback(lines[i]);
            }
        }
        int screenRow = 0;
        for (int i = firstScreenLine; i < lines.length && screenRow < rows; i++, screenRow++) {
            String line = lines[i];
            int length = Math.min(line.length(), columns);
            for (int c = 0; c < length; c++) {
                screen[screenRow][c] = line.charAt(c);
            }
        }
        row = clamp(Math.max(0, screenRow - 1), 0, rows - 1);
        column = screenRow == 0 ? 0 : clamp(trimRight(screen[row]).length(), 0, columns - 1);
    }

    private void clearScreen() {
        screen = new char[rows][columns];
        for (int r = 0; r < rows; r++) {
            fillLine(r, 0, columns);
        }
    }

    private void savePrimaryScreen() {
        primaryScrollback = new ArrayList<>(scrollback);
        primaryScreen = copyScreen(screen);
        primaryRow = row;
        primaryColumn = column;
    }

    private void restorePrimaryScreen() {
        if (primaryScreen == null || primaryScreen.length != rows || primaryScreen[0].length != columns) {
            clearScreen();
            row = 0;
            column = 0;
        } else {
            screen = copyScreen(primaryScreen);
            row = clamp(primaryRow, 0, rows - 1);
            column = clamp(primaryColumn, 0, columns - 1);
        }
        scrollback.clear();
        if (primaryScrollback != null) {
            scrollback.addAll(primaryScrollback);
        }
        primaryScrollback = null;
        primaryScreen = null;
    }

    private char[][] copyScreen(char[][] source) {
        char[][] copy = new char[rows][columns];
        for (int r = 0; r < rows; r++) {
            System.arraycopy(source[r], 0, copy[r], 0, columns);
        }
        return copy;
    }

    private void fillLine(int line, int start, int end) {
        int from = clamp(start, 0, columns);
        int to = clamp(end, 0, columns);
        for (int c = from; c < to; c++) {
            screen[line][c] = ' ';
        }
    }

    private String render() {
        StringBuilder out = new StringBuilder();
        if (!alternateScreen) {
            int start = Math.max(0, scrollback.size() - MAX_SCROLLBACK);
            for (int i = start; i < scrollback.size(); i++) {
                out.append(scrollback.get(i)).append('\n');
            }
        }
        int last = lastNonEmptyScreenLine();
        for (int r = 0; r <= last; r++) {
            out.append(trimRight(screen[r]));
            if (r < last) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private int lastNonEmptyScreenLine() {
        for (int r = rows - 1; r >= 0; r--) {
            if (!trimRight(screen[r]).isEmpty()) {
                return r;
            }
        }
        return 0;
    }

    private void addScrollback(String line) {
        scrollback.add(line);
        if (scrollback.size() > MAX_SCROLLBACK) {
            scrollback.remove(0);
        }
    }

    private void saveCursor() {
        savedRow = row;
        savedColumn = column;
    }

    private void restoreCursor() {
        row = clamp(savedRow, 0, rows - 1);
        column = clamp(savedColumn, 0, columns - 1);
    }

    private static int[] parseParams(String params) {
        if (params == null || params.isEmpty()) {
            return new int[0];
        }
        String[] parts = params.split(";");
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            int marker = Math.max(part.lastIndexOf('?'), Math.max(part.lastIndexOf('>'), part.lastIndexOf('!')));
            if (marker >= 0 && marker + 1 < part.length()) {
                part = part.substring(marker + 1);
            }
            try {
                values[i] = part.isEmpty() ? 0 : Integer.parseInt(part);
            } catch (NumberFormatException ignored) {
                values[i] = 0;
            }
        }
        return values;
    }

    private static int first(int[] values, int fallback) {
        return values.length == 0 || values[0] == 0 ? fallback : values[0];
    }

    private static int second(int[] values, int fallback) {
        return values.length < 2 || values[1] == 0 ? fallback : values[1];
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String trimRight(char[] chars) {
        int end = chars.length;
        while (end > 0 && chars[end - 1] == ' ') {
            end--;
        }
        return new String(chars, 0, end);
    }

    private enum State {
        NORMAL,
        ESC,
        CSI,
        OSC,
        OSC_ESC
    }
}
