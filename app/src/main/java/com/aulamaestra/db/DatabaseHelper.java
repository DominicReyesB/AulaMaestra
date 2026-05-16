package com.aulamaestra.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.aulamaestra.model.ChatMessage;
import com.aulamaestra.model.Post;
import com.aulamaestra.model.PostType;
import com.aulamaestra.model.Salon;
import com.aulamaestra.model.Student;
import com.aulamaestra.model.SubmissionRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "aula_maestra.db";
    private static final int VERSION = 1;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE teachers (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT UNIQUE NOT NULL," +
                "password TEXT NOT NULL);");
        db.execSQL("CREATE TABLE salons (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "teacher_id INTEGER NOT NULL," +
                "salon_number INTEGER NOT NULL," +
                "invite_code TEXT UNIQUE NOT NULL," +
                "UNIQUE(teacher_id, salon_number));");
        db.execSQL("CREATE TABLE students (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "display_name TEXT NOT NULL);");
        db.execSQL("CREATE TABLE enrollments (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "student_id INTEGER NOT NULL," +
                "salon_id INTEGER NOT NULL," +
                "UNIQUE(student_id, salon_id));");
        db.execSQL("CREATE TABLE posts (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "salon_id INTEGER NOT NULL," +
                "post_type INTEGER NOT NULL," +
                "title TEXT NOT NULL," +
                "body TEXT," +
                "file_path TEXT," +
                "created_at INTEGER NOT NULL);");
        db.execSQL("CREATE TABLE submissions (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "post_id INTEGER NOT NULL," +
                "student_id INTEGER NOT NULL," +
                "text_answer TEXT," +
                "file_path TEXT," +
                "submitted_at INTEGER NOT NULL," +
                "UNIQUE(post_id, student_id));");
        db.execSQL("CREATE TABLE grades (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "submission_id INTEGER NOT NULL UNIQUE," +
                "score REAL NOT NULL," +
                "feedback TEXT);");
        db.execSQL("CREATE TABLE messages (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "salon_id INTEGER NOT NULL," +
                "student_id INTEGER NOT NULL," +
                "from_teacher INTEGER NOT NULL," +
                "body TEXT NOT NULL," +
                "created_at INTEGER NOT NULL);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public long registerTeacher(String username, String password) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("username", username.trim().toLowerCase(Locale.ROOT));
        cv.put("password", password);
        long id = db.insert("teachers", null, cv);
        if (id > 0) {
            ensureSalonsForTeacher(id);
        }
        return id;
    }

    public long loginTeacher(String username, String password) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT _id FROM teachers WHERE username=? AND password=?",
                new String[]{username.trim().toLowerCase(Locale.ROOT), password})) {
            if (c.moveToFirst()) {
                long id = c.getLong(0);
                ensureSalonsForTeacher(id);
                return id;
            }
        }
        return -1L;
    }

    public void ensureSalonsForTeacher(long teacherId) {
        SQLiteDatabase db = getWritableDatabase();
        int[] nums = {601, 602, 603, 604, 605};
        for (int n : nums) {
            try (Cursor c = db.rawQuery(
                    "SELECT _id FROM salons WHERE teacher_id=? AND salon_number=?",
                    new String[]{String.valueOf(teacherId), String.valueOf(n)})) {
                if (!c.moveToFirst()) {
                    ContentValues cv = new ContentValues();
                    cv.put("teacher_id", teacherId);
                    cv.put("salon_number", n);
                    cv.put("invite_code", randomCode());
                    db.insert("salons", null, cv);
                }
            }
        }
    }

    private static String randomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public List<Salon> listSalonsForTeacher(long teacherId) {
        List<Salon> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT _id, teacher_id, salon_number, invite_code FROM salons WHERE teacher_id=? ORDER BY salon_number",
                new String[]{String.valueOf(teacherId)})) {
            while (c.moveToNext()) {
                list.add(new Salon(
                        c.getLong(0),
                        c.getLong(1),
                        c.getInt(2),
                        c.getString(3)));
            }
        }
        return list;
    }

    public Salon findSalonById(long salonId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT _id, teacher_id, salon_number, invite_code FROM salons WHERE _id=?",
                new String[]{String.valueOf(salonId)})) {
            if (c.moveToFirst()) {
                return new Salon(c.getLong(0), c.getLong(1), c.getInt(2), c.getString(3));
            }
        }
        return null;
    }

    public Salon findSalonByCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT _id, teacher_id, salon_number, invite_code FROM salons WHERE invite_code=?",
                new String[]{normalized})) {
            if (c.moveToFirst()) {
                return new Salon(c.getLong(0), c.getLong(1), c.getInt(2), c.getString(3));
            }
        }
        return null;
    }

    public long createStudent(String displayName) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("display_name", displayName.trim());
        return db.insert("students", null, cv);
    }

    public void enrollStudent(long studentId, long salonId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("student_id", studentId);
        cv.put("salon_id", salonId);
        db.insertWithOnConflict("enrollments", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public List<Student> listStudentsInSalon(long salonId) {
        List<Student> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT s._id, s.display_name FROM students s " +
                        "INNER JOIN enrollments e ON e.student_id=s._id WHERE e.salon_id=? ORDER BY s.display_name",
                new String[]{String.valueOf(salonId)})) {
            while (c.moveToNext()) {
                list.add(new Student(c.getLong(0), c.getString(1)));
            }
        }
        return list;
    }

    public long insertPost(long salonId, int postType, String title, String body, String filePath) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("salon_id", salonId);
        cv.put("post_type", postType);
        cv.put("title", title);
        cv.put("body", body);
        cv.put("file_path", filePath);
        cv.put("created_at", System.currentTimeMillis());
        return db.insert("posts", null, cv);
    }

    public List<Post> listPosts(long salonId, Integer typeFilter) {
        List<Post> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT _id, post_type, title, body, file_path, created_at FROM posts WHERE salon_id=?";
        String[] args;
        if (typeFilter == null) {
            sql += " ORDER BY created_at DESC";
            args = new String[]{String.valueOf(salonId)};
        } else {
            sql += " AND post_type=? ORDER BY created_at DESC";
            args = new String[]{String.valueOf(salonId), String.valueOf(typeFilter)};
        }
        try (Cursor c = db.rawQuery(sql, args)) {
            while (c.moveToNext()) {
                list.add(new Post(
                        c.getLong(0),
                        c.getInt(1),
                        c.getString(2),
                        c.getString(3),
                        c.getString(4),
                        c.getLong(5)));
            }
        }
        return list;
    }

    public void upsertSubmission(long postId, long studentId, String text, String filePath) {
        SQLiteDatabase db = getWritableDatabase();
        long existingId = -1L;
        try (Cursor c = db.rawQuery(
                "SELECT _id FROM submissions WHERE post_id=? AND student_id=?",
                new String[]{String.valueOf(postId), String.valueOf(studentId)})) {
            if (c.moveToFirst()) {
                existingId = c.getLong(0);
            }
        }
        ContentValues cv = new ContentValues();
        cv.put("text_answer", text);
        cv.put("file_path", filePath);
        cv.put("submitted_at", System.currentTimeMillis());
        if (existingId >= 0) {
            db.update("submissions", cv, "_id=?", new String[]{String.valueOf(existingId)});
        } else {
            cv.put("post_id", postId);
            cv.put("student_id", studentId);
            db.insert("submissions", null, cv);
        }
    }

    public List<SubmissionRow> listSubmissionsForSalon(long salonId) {
        List<SubmissionRow> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT sub._id, sub.post_id, p.title, sub.student_id, st.display_name, " +
                "sub.text_answer, sub.file_path, sub.submitted_at, g.score, g.feedback " +
                "FROM submissions sub " +
                "INNER JOIN posts p ON p._id=sub.post_id " +
                "INNER JOIN students st ON st._id=sub.student_id " +
                "LEFT JOIN grades g ON g.submission_id=sub._id " +
                "WHERE p.salon_id=? AND p.post_type=? ORDER BY sub.submitted_at DESC";
        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(salonId), String.valueOf(PostType.ASSIGNMENT)})) {
            while (c.moveToNext()) {
                Double score = c.isNull(8) ? null : c.getDouble(8);
                list.add(new SubmissionRow(
                        c.getLong(0),
                        c.getLong(1),
                        c.getString(2),
                        c.getLong(3),
                        c.getString(4),
                        c.getString(5),
                        c.getString(6),
                        c.getLong(7),
                        score,
                        c.isNull(9) ? null : c.getString(9)));
            }
        }
        return list;
    }

    public List<SubmissionRow> listSubmissionsForStudent(long salonId, long studentId) {
        List<SubmissionRow> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT sub._id, sub.post_id, p.title, sub.student_id, st.display_name, " +
                "sub.text_answer, sub.file_path, sub.submitted_at, g.score, g.feedback " +
                "FROM submissions sub " +
                "INNER JOIN posts p ON p._id=sub.post_id " +
                "INNER JOIN students st ON st._id=sub.student_id " +
                "LEFT JOIN grades g ON g.submission_id=sub._id " +
                "WHERE p.salon_id=? AND sub.student_id=? ORDER BY sub.submitted_at DESC";
        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(salonId), String.valueOf(studentId)})) {
            while (c.moveToNext()) {
                Double score = c.isNull(8) ? null : c.getDouble(8);
                list.add(new SubmissionRow(
                        c.getLong(0),
                        c.getLong(1),
                        c.getString(2),
                        c.getLong(3),
                        c.getString(4),
                        c.getString(5),
                        c.getString(6),
                        c.getLong(7),
                        score,
                        c.isNull(9) ? null : c.getString(9)));
            }
        }
        return list;
    }

    public void saveGrade(long submissionId, double score, String feedback) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("submission_id", submissionId);
        cv.put("score", score);
        cv.put("feedback", feedback);
        db.insertWithOnConflict("grades", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void insertMessage(long salonId, long studentId, boolean fromTeacher, String body) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("salon_id", salonId);
        cv.put("student_id", studentId);
        cv.put("from_teacher", fromTeacher ? 1 : 0);
        cv.put("body", body);
        cv.put("created_at", System.currentTimeMillis());
        db.insert("messages", null, cv);
    }

    public List<ChatMessage> listMessages(long salonId, long studentId) {
        List<ChatMessage> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT _id, from_teacher, body, created_at FROM messages WHERE salon_id=? AND student_id=? ORDER BY created_at ASC",
                new String[]{String.valueOf(salonId), String.valueOf(studentId)})) {
            while (c.moveToNext()) {
                list.add(new ChatMessage(
                        c.getLong(0),
                        c.getInt(1) == 1,
                        c.getString(2),
                        c.getLong(3)));
            }
        }
        return list;
    }

    public Student getStudent(long studentId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT _id, display_name FROM students WHERE _id=?",
                new String[]{String.valueOf(studentId)})) {
            if (c.moveToFirst()) {
                return new Student(c.getLong(0), c.getString(1));
            }
        }
        return null;
    }

    public void updateStudentName(long studentId, String displayName) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("display_name", displayName.trim());
        db.update("students", cv, "_id=?", new String[]{String.valueOf(studentId)});
    }
}
