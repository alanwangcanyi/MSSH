package com.mssh.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class SshHostRepository {
    private final MsshDatabaseHelper dbHelper;
    private final CredentialCipher credentialCipher;

    public SshHostRepository(MsshDatabaseHelper dbHelper, CredentialCipher credentialCipher) {
        this.dbHelper = dbHelper;
        this.credentialCipher = credentialCipher;
    }

    public List<SshHost> listHosts() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query("ssh_hosts", null, null, null, null, null, "updated_at DESC");
        try {
            List<SshHost> hosts = new ArrayList<>();
            while (cursor.moveToNext()) {
                hosts.add(readHost(cursor));
            }
            return hosts;
        } finally {
            cursor.close();
        }
    }

    public long save(SshHost host) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("name", clean(host.name));
        values.put("hostname", clean(host.hostname));
        values.put("protocol", cleanProtocol(host.protocol));
        values.put("port", host.port <= 0 ? 22 : host.port);
        values.put("username", clean(host.username));
        values.put("save_password", host.savePassword ? 1 : 0);
        values.put("password_secret", host.savePassword ? credentialCipher.encrypt(host.password) : "");
        values.put("auth_type", host.authType == null ? "PASSWORD" : host.authType);
        values.put("updated_at", now);

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        if (host.id > 0) {
            db.update("ssh_hosts", values, "id = ?", new String[]{String.valueOf(host.id)});
            return host.id;
        }

        values.put("created_at", now);
        return db.insertOrThrow("ssh_hosts", null, values);
    }

    public void delete(long id) {
        dbHelper.getWritableDatabase().delete("ssh_hosts", "id = ?", new String[]{String.valueOf(id)});
    }

    private SshHost readHost(Cursor cursor) {
        SshHost host = new SshHost();
        host.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        host.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
        host.hostname = cursor.getString(cursor.getColumnIndexOrThrow("hostname"));
        host.protocol = readProtocol(cursor);
        host.port = cursor.getInt(cursor.getColumnIndexOrThrow("port"));
        host.username = cursor.getString(cursor.getColumnIndexOrThrow("username"));
        host.savePassword = readSavePassword(cursor);
        host.password = readPassword(cursor);
        host.authType = cursor.getString(cursor.getColumnIndexOrThrow("auth_type"));
        host.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        host.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
        return host;
    }

    private String readPassword(Cursor cursor) {
        int index = cursor.getColumnIndex("password_secret");
        if (index < 0) {
            return "";
        }
        String secret = cursor.getString(index);
        return credentialCipher.decrypt(secret);
    }

    private static boolean readSavePassword(Cursor cursor) {
        int index = cursor.getColumnIndex("save_password");
        if (index < 0) {
            int secretIndex = cursor.getColumnIndex("password_secret");
            return secretIndex >= 0 && cursor.getString(secretIndex) != null && !cursor.getString(secretIndex).trim().isEmpty();
        }
        return cursor.getInt(index) == 1;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String cleanProtocol(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "SSH";
        }
        String protocol = value.trim().toUpperCase();
        return "SFTP".equals(protocol) ? "SFTP" : "SSH";
    }

    private static String readProtocol(Cursor cursor) {
        int index = cursor.getColumnIndex("protocol");
        if (index < 0) {
            return "SSH";
        }
        String protocol = cursor.getString(index);
        return cleanProtocol(protocol);
    }
}
