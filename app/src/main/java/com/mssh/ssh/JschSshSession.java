package com.mssh.ssh;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class JschSshSession implements SshSessionHandle {
    private final SshConnectionConfig config;
    private final File knownHostsFile;
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor();
    private Session session;
    private ChannelShell channel;
    private OutputStream outputStream;
    private volatile boolean closed;
    private int pendingColumns = 80;
    private int pendingRows = 32;

    JschSshSession(SshConnectionConfig config, File knownHostsFile) {
        this.config = config;
        this.knownHostsFile = knownHostsFile;
    }

    @Override
    public void connect(SshSessionListener listener) {
        Thread thread = new Thread(() -> {
            try {
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

                channel = (ChannelShell) session.openChannel("shell");
                channel.setPty(true);
                channel.setPtyType("xterm-256color");
                channel.setPtySize(pendingColumns, pendingRows, pendingColumns * 8, pendingRows * 16);
                InputStream inputStream = channel.getInputStream();
                outputStream = channel.getOutputStream();
                channel.connect(10_000);
                listener.onConnected();
                readLoop(inputStream, listener);
            } catch (JSchException | IOException e) {
                if (!closed) {
                    listener.onError(e.getMessage(), e);
                }
            } finally {
                disconnectInternal();
                if (!closed) {
                    listener.onDisconnected();
                }
            }
        }, "mssh-jsch-session");
        thread.start();
    }

    @Override
    public void sendInput(String input) {
        if (closed) {
            return;
        }
        try {
            writeExecutor.execute(() -> {
                synchronized (JschSshSession.this) {
                    if (outputStream == null || closed) {
                        return;
                    }
                    try {
                        outputStream.write(input.getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                    } catch (IOException ignored) {
                        disconnect();
                    }
                }
            });
        } catch (RuntimeException ignored) {
            // The writer may already be shutting down during disconnect.
        }
    }

    @Override
    public synchronized void resizePty(int columns, int rows) {
        pendingColumns = Math.max(20, columns);
        pendingRows = Math.max(8, rows);
        if (channel != null && channel.isConnected()) {
            channel.setPtySize(pendingColumns, pendingRows, pendingColumns * 8, pendingRows * 16);
        }
    }

    @Override
    public void disconnect() {
        closed = true;
        disconnectInternal();
    }

    private void readLoop(InputStream inputStream, SshSessionListener listener) throws IOException {
        byte[] buffer = new byte[4096];
        while (!closed && channel != null && channel.isConnected()) {
            int count = inputStream.read(buffer);
            if (count < 0) {
                break;
            }
            if (count > 0) {
                listener.onOutput(new String(buffer, 0, count, StandardCharsets.UTF_8));
            }
        }
    }

    private synchronized void disconnectInternal() {
        if (channel != null) {
            channel.disconnect();
            channel = null;
        }
        if (session != null) {
            session.disconnect();
            session = null;
        }
        writeExecutor.shutdownNow();
    }

}
