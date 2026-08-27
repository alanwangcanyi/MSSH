package com.mssh.ssh;

public class SshConnectionConfig {
    public final long hostId;
    public final String host;
    public final int port;
    public final String username;
    public final String password;

    public SshConnectionConfig(long hostId, String host, int port, String username, String password) {
        this.hostId = hostId;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }
}
