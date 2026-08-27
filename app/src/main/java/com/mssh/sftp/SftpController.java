package com.mssh.sftp;

import android.os.Handler;
import android.os.Looper;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.mssh.ssh.JschKnownHosts;
import com.mssh.ssh.SshConnectionConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SftpController {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final File knownHostsFile;
    private SftpStateListener stateListener;
    private Session session;
    private ChannelSftp channel;

    public SftpController(File knownHostsFile) {
        this.knownHostsFile = knownHostsFile;
    }

    public void setStateListener(SftpStateListener stateListener) {
        this.stateListener = stateListener;
    }

    public void connect(SshConnectionConfig config) {
        executor.execute(() -> {
            try {
                postStatus("connecting");
                disconnectInternal();

                JSch jsch = new JSch();
                JschKnownHosts.configure(jsch, knownHostsFile);
                session = jsch.getSession(config.username, config.host, config.port);
                session.setPassword(config.password);
                session.setUserInfo(JschKnownHosts.autoAcceptUnknownHostUserInfo());

                Properties properties = new Properties();
                properties.put("StrictHostKeyChecking", "ask");
                properties.put("PreferredAuthentications", "password,keyboard-interactive");
                session.setConfig(properties);
                session.connect(15_000);

                channel = (ChannelSftp) session.openChannel("sftp");
                channel.connect(10_000);
                postStatus("connected");
                publishCurrentDirectory();
            } catch (Exception e) {
                postStatus("error");
                postError(safe(e.getMessage()), e);
                disconnectInternal();
            }
        });
    }

    public void refresh() {
        executor.execute(() -> {
            try {
                publishCurrentDirectory();
            } catch (Exception e) {
                postError(safe(e.getMessage()), e);
            }
        });
    }

    public void changeDirectory(String path) {
        executor.execute(() -> {
            try {
                if (channel == null || !channel.isConnected()) {
                    postError("SFTP 未连接", null);
                    return;
                }
                channel.cd(path);
                publishCurrentDirectory();
            } catch (Exception e) {
                postError(safe(e.getMessage()), e);
            }
        });
    }

    public void up() {
        changeDirectory("..");
    }

    public void disconnect() {
        executor.execute(this::disconnectInternal);
    }

    private void publishCurrentDirectory() throws Exception {
        if (channel == null || !channel.isConnected()) {
            postError("SFTP 未连接", null);
            return;
        }
        String path = channel.pwd();
        Vector<?> rawEntries = channel.ls(".");
        List<SftpEntry> entries = new ArrayList<>();
        for (Object item : rawEntries) {
            ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) item;
            String name = entry.getFilename();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            entries.add(new SftpEntry(name, entry.getAttrs().isDir(), entry.getAttrs().getSize()));
        }
        mainHandler.post(() -> {
            if (stateListener != null) {
                stateListener.onDirectoryChanged(path, entries);
            }
        });
    }

    private void disconnectInternal() {
        if (channel != null) {
            channel.disconnect();
            channel = null;
        }
        if (session != null) {
            session.disconnect();
            session = null;
        }
        postStatus("disconnected");
    }

    private void postStatus(String status) {
        mainHandler.post(() -> {
            if (stateListener != null) {
                stateListener.onStatusChanged(status);
            }
        });
    }

    private void postError(String message, Throwable throwable) {
        mainHandler.post(() -> {
            if (stateListener != null) {
                stateListener.onError(message, throwable);
            }
        });
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "unknown" : value;
    }
}
