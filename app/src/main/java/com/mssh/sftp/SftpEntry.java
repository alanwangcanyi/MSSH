package com.mssh.sftp;

public class SftpEntry {
    public final String name;
    public final boolean directory;
    public final long size;

    public SftpEntry(String name, boolean directory, long size) {
        this.name = name;
        this.directory = directory;
        this.size = size;
    }
}
