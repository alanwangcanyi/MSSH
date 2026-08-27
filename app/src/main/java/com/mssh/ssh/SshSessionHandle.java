package com.mssh.ssh;

public interface SshSessionHandle {
    void connect(SshSessionListener listener);

    void sendInput(String input);

    void resizePty(int columns, int rows);

    void disconnect();
}
