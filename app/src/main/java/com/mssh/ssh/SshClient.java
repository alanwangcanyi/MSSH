package com.mssh.ssh;

public interface SshClient {
    SshSessionHandle createSession(SshConnectionConfig config);
}
