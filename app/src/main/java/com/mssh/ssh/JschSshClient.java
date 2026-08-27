package com.mssh.ssh;

import java.io.File;

public class JschSshClient implements SshClient {
    private final File knownHostsFile;

    public JschSshClient(File knownHostsFile) {
        this.knownHostsFile = knownHostsFile;
    }

    @Override
    public SshSessionHandle createSession(SshConnectionConfig config) {
        return new JschSshSession(config, knownHostsFile);
    }
}
