package com.aulamaestra.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.aulamaestra.R;

public final class ExternalLinkUtils {
    private ExternalLinkUtils() {
    }

    public static void open(Context context, String value) {
        if (value == null || value.trim().isEmpty()) return;
        String clean = value.trim();
        if (!clean.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) clean = "https://" + clean;
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(clean));
        try {
            context.startActivity(Intent.createChooser(view, context.getString(R.string.open_attachment)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.cannot_open_attachment, Toast.LENGTH_LONG).show();
        }
    }
}
