package com.mssh.terminal;

public interface TerminalStateListener {
    void onTextChanged(String text);

    void onStatusChanged(String status);

    void onLogStateChanged(boolean recording, String path);
}
