package com.aulamaestra.ui;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public final class IoUtils {
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
