package com.mssh.data;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

public class SshLogRepository {
    private final MsshDatabaseHelper dbHelper;

    public SshLogRepository(MsshDatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public long insertStarted(Long hostId, String fileName, String absolutePath, long startedAt) {
        ContentValues values = new ContentValues();
        if (hostId != null) {
            values.put("host_id", hostId);
        }
        values.put("file_name", fileName);
        values.put("absolute_path", absolutePath);
        values.put("started_at", startedAt);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.insertOrThrow("ssh_logs", null, values);
    }

    public void markEnded(long id, long endedAt) {
        ContentValues values = new ContentValues();
        values.put("ended_at", endedAt);
        dbHelper.getWritableDatabase().update("ssh_logs", values, "id = ?", new String[]{String.valueOf(id)});
    }
}
