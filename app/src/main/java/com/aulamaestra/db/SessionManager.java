package com.aulamaestra.db;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF = "aula_session";
    private static final String KEY_TEACHER_ID = "teacher_id";
    private static final String KEY_STUDENT_ID = "student_id";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public void setTeacherId(long id) {
        prefs.edit().putLong(KEY_TEACHER_ID, id).apply();
    }

    public long getTeacherId() {
        return prefs.getLong(KEY_TEACHER_ID, -1L);
    }

    public void clearTeacher() {
        prefs.edit().remove(KEY_TEACHER_ID).apply();
    }

    public void setStudentId(long id) {
        prefs.edit().putLong(KEY_STUDENT_ID, id).apply();
    }

    public long getStudentId() {
        return prefs.getLong(KEY_STUDENT_ID, -1L);
    }

    public void clearStudent() {
        prefs.edit().remove(KEY_STUDENT_ID).apply();
    }
}
