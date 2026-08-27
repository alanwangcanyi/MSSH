package com.mssh.ssh;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.UserInfo;

import java.io.File;
import java.io.IOException;

public final class JschKnownHosts {
    private JschKnownHosts() {
    }

    public static void configure(JSch jsch, File knownHostsFile) throws IOException, JSchException {
        prepareFile(knownHostsFile);
        jsch.setKnownHosts(knownHostsFile.getAbsolutePath());
    }

    public static UserInfo autoAcceptUnknownHostUserInfo() {
        return new AutoAcceptHostKeyUserInfo();
    }

    private static void prepareFile(File knownHostsFile) throws IOException {
        File parent = knownHostsFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建 known_hosts 目录：" + parent.getAbsolutePath());
        }
        if (!knownHostsFile.exists() && !knownHostsFile.createNewFile()) {
            throw new IOException("无法创建 known_hosts 文件：" + knownHostsFile.getAbsolutePath());
        }
    }

    private static class AutoAcceptHostKeyUserInfo implements UserInfo {
        @Override
        public String getPassphrase() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean promptPassword(String message) {
            return false;
        }

        @Override
        public boolean promptPassphrase(String message) {
            return false;
        }

        @Override
        public boolean promptYesNo(String message) {
            return true;
        }

        @Override
        public void showMessage(String message) {
        }
    }
}
