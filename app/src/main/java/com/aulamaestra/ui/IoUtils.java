package com.aulamaestra.ui;

import android.content.Context;
import android.net.Uri;

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
        File out = new File(dir, fileName);
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
}
