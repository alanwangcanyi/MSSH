package com.mssh.logging;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.security.SecureRandom;

public class LogFileNameGenerator {
    private static final char[] RANDOM_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private final SecureRandom random = new SecureRandom();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);

    public synchronized String newFileName(long timestampMillis) {
        StringBuilder code = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            code.append(RANDOM_CHARS[random.nextInt(RANDOM_CHARS.length)]);
        }
        return dateFormat.format(new Date(timestampMillis)) + "-" + code + ".log";
    }
}
