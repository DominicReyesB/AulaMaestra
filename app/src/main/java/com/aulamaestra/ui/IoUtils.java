package com.aulamaestra.ui;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class IoUtils {
    private static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface CopyCallback {
        void onComplete(String path);
    }

    private IoUtils() {
    }

    public static String copyUriToFilesDir(Context context, Uri uri, String fileName) {
        if (uri == null) {
            return null;
        }
        File dir = new File(context.getFilesDir(), "uploads");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        cleanupOldUploads(dir);
        String originalName = displayName(context, uri);
        String safeOriginal = originalName == null ? "" : originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String finalName = safeOriginal.isEmpty() ? fileName : fileName + "-" + safeOriginal;
        File out = new File(dir, finalName);
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream outStream = new FileOutputStream(out)) {
            if (in == null) {
                return null;
            }
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                outStream.write(buf, 0, n);
            }
            return out.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    public static void copyUriToFilesDirAsync(Context context, Uri uri, String fileName,
                                               CopyCallback callback) {
        Context appContext = context.getApplicationContext();
        IO_EXECUTOR.execute(() -> {
            String path = copyUriToFilesDir(appContext, uri, fileName);
            MAIN.post(() -> callback.onComplete(path));
        });
    }

    private static void cleanupOldUploads(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
        for (File file : files) {
            if (file.isFile() && file.lastModified() < cutoff) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
