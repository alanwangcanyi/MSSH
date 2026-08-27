package com.mssh.sftp;

import java.util.List;

public interface SftpStateListener {
    void onStatusChanged(String status);

    void onDirectoryChanged(String path, List<SftpEntry> entries);

    void onError(String message, Throwable throwable);
}
