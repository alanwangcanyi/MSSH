package com.mssh.localcmd;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.mssh.logging.SshLogWriter;
import com.mssh.terminal.TerminalStateListener;
import com.mssh.terminal.TerminalOutputSanitizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalCommandController {
    private static final int MAX_BUFFER_CHARS = 160_000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SshLogWriter logWriter;
    private final TerminalOutputSanitizer displaySanitizer = new TerminalOutputSanitizer();
    private final TerminalOutputSanitizer logSanitizer = new TerminalOutputSanitizer();
    private final StringBuilder buffer = new StringBuilder();
    private File workingDirectory;
    private TerminalStateListener stateListener;
    private Process currentProcess;

    public LocalCommandController(Context context, SshLogWriter logWriter) {
        this.workingDirectory = context.getFilesDir();
        this.logWriter = logWriter;
    }

    public void setStateListener(TerminalStateListener stateListener) {
        this.stateListener = stateListener;
        if (stateListener == null) {
            return;
        }
        postStatus("local");
        appendLocalLine("本地 CMD 已打开。当前目录：" + workingDirectory.getAbsolutePath());
    }

    public void runCommand(String command) {
        String value = command == null ? "" : command.trim();
        if (value.isEmpty()) {
            return;
        }
        if (handleBuiltin(value)) {
            return;
        }

        executor.execute(() -> {
            appendLocalLine("$ " + value);
            ProcessBuilder builder = new ProcessBuilder("/system/bin/sh", "-c", value);
            builder.directory(workingDirectory);
            builder.redirectErrorStream(true);
            try {
                postStatus("running");
                currentProcess = builder.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        currentProcess.getInputStream(),
                        StandardCharsets.UTF_8
                ))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        append(line + "\n");
                    }
                }
                int exitCode = currentProcess.waitFor();
                appendLocalLine("命令结束，退出码：" + exitCode);
            } catch (IOException e) {
                appendLocalLine("命令执行失败：" + safe(e.getMessage()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                appendLocalLine("命令被中断。");
            } finally {
                currentProcess = null;
                postStatus("local");
            }
        });
    }

    public void sendRaw(String value) {
        if ("\u0003".equals(value)) {
            stopCurrentProcess();
        }
    }

    public void toggleLogging() {
        if (logWriter.isRecording()) {
            stopLogging();
            return;
        }
        try {
            String path = logWriter.start(null);
            postLogState(true, path);
            appendLocalLine("日志已开始：" + logWriter.currentFileName());
        } catch (IOException e) {
            appendLocalLine("无法开始日志：" + safe(e.getMessage()));
            postLogState(false, null);
        }
    }

    public void close() {
        stopCurrentProcess();
        stopLogging();
    }

    private boolean handleBuiltin(String command) {
        if ("pwd".equals(command)) {
            appendLocalLine("$ pwd");
            append(workingDirectory.getAbsolutePath() + "\n");
            return true;
        }
        if (command.equals("cd") || command.startsWith("cd ")) {
            appendLocalLine("$ " + command);
            String target = command.length() == 2 ? System.getProperty("user.home", "/") : command.substring(3).trim();
            File next = target.startsWith("/") ? new File(target) : new File(workingDirectory, target);
            try {
                next = next.getCanonicalFile();
            } catch (IOException ignored) {
                next = next.getAbsoluteFile();
            }
            if (!next.exists() || !next.isDirectory()) {
                appendLocalLine("目录不存在：" + next.getAbsolutePath());
                return true;
            }
            workingDirectory = next;
            append(workingDirectory.getAbsolutePath() + "\n");
            return true;
        }
        return false;
    }

    private void stopCurrentProcess() {
        Process process = currentProcess;
        if (process != null) {
            process.destroy();
            appendLocalLine("已请求停止当前命令。");
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
            appendLocalLine("日志已保存：" + fileName);
        }
    }

    private void appendLocalLine(String value) {
        append("\n[MSSH] " + value + "\n");
    }

    private void append(String value) {
        if (logWriter.isRecording()) {
            logWriter.write(logSanitizer.sanitizeChunk(value));
        }
        String displayValue = displaySanitizer.sanitizeChunk(value);
        if (displayValue.isEmpty()) {
            return;
        }
        mainHandler.post(() -> {
            buffer.append(displayValue);
            if (buffer.length() > MAX_BUFFER_CHARS) {
                buffer.delete(0, buffer.length() - MAX_BUFFER_CHARS);
            }
            if (stateListener != null) {
                stateListener.onTextChanged(buffer.toString());
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
