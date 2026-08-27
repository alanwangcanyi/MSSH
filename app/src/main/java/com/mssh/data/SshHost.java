package com.mssh.data;

public class SshHost {
    public long id;
    public String name;
    public String hostname;
    public String protocol;
    public int port;
    public String username;
    public String password;
    public boolean savePassword;
    public String authType;
    public long createdAt;
    public long updatedAt;

    public String displayName() {
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }
        return username + "@" + hostname + ":" + port;
    }

    public String protocolOrDefault() {
        return protocol == null || protocol.trim().isEmpty() ? "SSH" : protocol.trim().toUpperCase();
    }
}
