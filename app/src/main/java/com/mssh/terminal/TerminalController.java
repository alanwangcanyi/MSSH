package com.mssh.terminal;

import android.os.Handler;
import android.os.Looper;

import com.mssh.logging.SshLogWriter;
import com.mssh.ssh.SshClient;
import com.mssh.ssh.SshConnectionConfig;
import com.mssh.ssh.SshSessionHandle;
import com.mssh.ssh.SshSessionListener;

import java.io.IOException;

public class TerminalController {
    private static final int DEFAULT_COLUMNS = 80;
    private static final int DEFAULT_ROWS = 32;

    private final SshClient sshClient;
    private final SshLogWriter logWriter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TerminalScreenBuffer screenBuffer = new TerminalScreenBuffer();
    private final TerminalOutputSanitizer logSanitizer = new TerminalOutputSanitizer();
    private TerminalStateListener stateListener;
    private SshSessionHandle session;
    private SshConnectionConfig currentConfig;
    private int currentColumns = DEFAULT_COLUMNS;
    private int currentRows = DEFAULT_ROWS;

    public TerminalController(SshClient sshClient, SshLogWriter logWriter) {
        this.sshClient = sshClient;
        this.logWriter = logWriter;
    }

    public void setStateListener(TerminalStateListener stateListener) {
        this.stateListener = stateListener;
    }

    public void connect(SshConnectionConfig config) {
        currentConfig = config;
        screenBuffer.reset();
        appendLocalLine("Connecting to " + config.username + "@" + config.host + ":" + config.port + " ...");
        session = sshClient.createSession(config);
        session.connect(new SshSessionListener() {
            @Override
            public void onConnected() {
                if (session != null) {
                    session.resizePty(currentColumns, currentRows);
                }
                postStatus("connected");
            }

            @Override
            public void onOutput(String output) {
                if (logWriter.isRecording()) {
                    logWriter.write(logSanitizer.sanitizeChunk(output));
                }
                append(output);
            }

            @Override
            public void onDisconnected() {
                postStatus("disconnected");
                appendLocalLine("Disconnected.");
                stopLogging();
            }

            @Override
            public void onError(String message, Throwable throwable) {
                postStatus("error");
                appendLocalLine("SSH error: " + safe(message));
            }
        });
    }

    public void sendCommand(String command) {
        if (session == null) {
            appendLocalLine("No active SSH session.");
            return;
        }
        session.sendInput(command + "\n");
    }

    public void sendRaw(String value) {
        if (session != null) {
            session.sendInput(value);
        }
    }

    public boolean isAlternateScreen() {
        return screenBuffer.isAlternateScreen();
    }

    public void resizeTerminal(int columns, int rows) {
        currentColumns = Math.max(20, columns);
        currentRows = Math.max(8, rows);
        screenBuffer.resize(currentColumns, currentRows);
        if (session != null) {
            session.resizePty(currentColumns, currentRows);
        }
    }

    public void disconnect() {
        if (session != null) {
            session.disconnect();
            session = null;
        }
        stopLogging();
        postStatus("disconnected");
    }

    public void toggleLogging() {
        if (logWriter.isRecording()) {
            stopLogging();
            return;
        }
        try {
            Long hostId = currentConfig == null || currentConfig.hostId <= 0 ? null : currentConfig.hostId;
            String path = logWriter.start(hostId);
            postLogState(true, path);
            appendLocalLine("Log started: " + logWriter.currentFileName());
        } catch (IOException e) {
            appendLocalLine("Unable to start log: " + e.getMessage());
            postLogState(false, null);
        }
    }

    private void stopLogging() {
        if (!logWriter.isRecording()) {
            postLogState(false, null);
            return;
        }
        String path = logWriter.currentPath();
        String fileName = logWriter.currentFileName();
        logWriter.close();
        postLogState(false, path);
        if (fileName != null) {
            appendLocalLine("Log saved: " + fileName);
        }
    }

    private void appendLocalLine(String value) {
        append("\n[MSSH] " + value + "\n");
    }

    private void append(String value) {
        String displayValue = screenBuffer.append(value);
        mainHandler.post(() -> {
            if (stateListener != null) {
                stateListener.onTextChanged(displayValue);
            }
        });
    }

    private void postStatus(String status) {
        mainHandler.post(() -> {
            if (stateListener != null) {
                stateListener.onStatusChanged(status);
            }
        });
    }

    private void postLogState(boolean recording, String path) {
        mainHandler.post(() -> {
            if (stateListener != null) {
                stateListener.onLogStateChanged(recording, path);
            }
        });
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "unknown" : value;
    }
}
