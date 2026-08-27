package com.mssh.logging;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.mssh.data.SshLogRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class SshLogWriter {
    private static final String LOG_DIRECTORY_NAME = "mssh_log";
    private static final String PUBLIC_LOG_PATH = "/sdcard/Download/" + LOG_DIRECTORY_NAME;

    private final Context context;
    private final SshLogRepository repository;
    private final LogFileNameGenerator fileNameGenerator = new LogFileNameGenerator();
    private OutputStream stream;
    private long logId = -1;
    private String currentFileName;
    private String currentPath;

    public SshLogWriter(Context context, SshLogRepository repository) {
        this.context = context.getApplicationContext();
        this.repository = repository;
    }

    public synchronized boolean isRecording() {
        return stream != null;
    }

    public synchronized String currentFileName() {
        return currentFileName;
    }

    public synchronized String currentPath() {
        return currentPath;
    }

    public synchronized String start(Long hostId) throws IOException {
        if (stream != null) {
            return currentPath;
        }
        long now = System.currentTimeMillis();
        currentFileName = fileNameGenerator.newFileName(now);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openWithMediaStore(currentFileName);
        } else {
            openWithPublicFile(currentFileName);
        }
        logId = repository.insertStarted(hostId, currentFileName, currentPath, now);
        return currentPath;
    }

    private void openWithMediaStore(String fileName) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + LOG_DIRECTORY_NAME);

        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("无法创建日志文件：" + PUBLIC_LOG_PATH + "/" + fileName);
        }
        OutputStream outputStream = resolver.openOutputStream(uri, "w");
        if (outputStream == null) {
            throw new IOException("无法打开日志文件：" + PUBLIC_LOG_PATH + "/" + fileName);
        }
        stream = outputStream;
        currentPath = PUBLIC_LOG_PATH + "/" + fileName;
    }

    private void openWithPublicFile(String fileName) throws IOException {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), LOG_DIRECTORY_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建日志目录：" + dir.getAbsolutePath());
        }
        File file = new File(dir, fileName);
        stream = new FileOutputStream(file, false);
        currentPath = file.getAbsolutePath();
    }

    public synchronized void write(String output) {
        if (stream == null || output == null || output.isEmpty()) {
            return;
        }
        try {
            stream.write(output.getBytes(StandardCharsets.UTF_8));
            stream.flush();
        } catch (IOException ignored) {
            close();
        }
    }

    public synchronized void close() {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
            // Closing a log should not crash the terminal screen.
        } finally {
            stream = null;
            if (logId > 0) {
                repository.markEnded(logId, System.currentTimeMillis());
            }
            currentFileName = null;
            currentPath = null;
            logId = -1;
        }
    }
}
