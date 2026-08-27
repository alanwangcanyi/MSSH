package com.mssh.ui;

import android.widget.TextView;

public final class TerminalLayoutSizer {
    private static final int MIN_COLUMNS = 20;
    private static final int MIN_ROWS = 8;

    private TerminalLayoutSizer() {
    }

    public static Size measure(TextView terminalText, int viewportWidth, int viewportHeight) {
        if (terminalText == null || viewportWidth <= 0 || viewportHeight <= 0) {
            return null;
        }

        float charWidth = Math.max(1f, terminalText.getPaint().measureText("W"));
        float lineHeight = Math.max(1f, terminalText.getLineHeight());
        int usableWidth = Math.max(0,
                viewportWidth - terminalText.getPaddingLeft() - terminalText.getPaddingRight());
        int usableHeight = Math.max(0,
                viewportHeight - terminalText.getPaddingTop() - terminalText.getPaddingBottom());

        int columns = Math.max(MIN_COLUMNS, (int) Math.floor(usableWidth / charWidth));
        int rows = Math.max(MIN_ROWS, (int) Math.floor(usableHeight / lineHeight));
        return new Size(columns, rows);
    }

    public static final class Size {
        public final int columns;
        public final int rows;

        private Size(int columns, int rows) {
            this.columns = columns;
            this.rows = rows;
        }
    }
}
