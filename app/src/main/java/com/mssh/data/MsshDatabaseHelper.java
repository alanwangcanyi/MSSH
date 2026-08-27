package com.mssh.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class MsshDatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "mssh.db";
    private static final int DB_VERSION = 4;

    public MsshDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE ssh_hosts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "hostname TEXT NOT NULL," +
                "protocol TEXT NOT NULL DEFAULT 'SSH'," +
                "port INTEGER NOT NULL," +
                "username TEXT NOT NULL," +
                "save_password INTEGER NOT NULL DEFAULT 0," +
                "password_secret TEXT," +
                "auth_type TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE TABLE connection_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "host_id INTEGER," +
                "hostname TEXT NOT NULL," +
                "username TEXT NOT NULL," +
                "connected_at INTEGER NOT NULL," +
                "status TEXT NOT NULL" +
                ")");
        db.execSQL("CREATE TABLE ssh_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "host_id INTEGER," +
                "file_name TEXT NOT NULL," +
                "absolute_path TEXT NOT NULL," +
                "started_at INTEGER NOT NULL," +
                "ended_at INTEGER" +
                ")");
        db.execSQL("CREATE TABLE terminal_preferences (" +
                "id INTEGER PRIMARY KEY," +
                "font_size_sp INTEGER NOT NULL," +
                "dark_theme INTEGER NOT NULL," +
                "show_shortcut_bar INTEGER NOT NULL" +
                ")");
        db.execSQL("INSERT INTO terminal_preferences " +
                "(id, font_size_sp, dark_theme, show_shortcut_bar) VALUES (1, 14, 1, 1)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE ssh_hosts ADD COLUMN password_secret TEXT");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE ssh_hosts ADD COLUMN protocol TEXT NOT NULL DEFAULT 'SSH'");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE ssh_hosts ADD COLUMN save_password INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE ssh_hosts SET save_password = 1 " +
                    "WHERE password_secret IS NOT NULL AND length(trim(password_secret)) > 0");
        }
    }
}
