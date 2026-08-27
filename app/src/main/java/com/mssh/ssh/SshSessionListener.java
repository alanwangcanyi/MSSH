package com.mssh.ssh;

public interface SshSessionListener {
    void onConnected();

    void onOutput(String output);

    void onDisconnected();

    void onError(String message, Throwable throwable);
}
