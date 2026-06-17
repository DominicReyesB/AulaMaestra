package com.aulamaestra.util;

import com.aulamaestra.model.SubmissionAttachment;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class SubmissionAttachments {
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<SubmissionAttachment>>() {}.getType();

    private SubmissionAttachments() {
    }

    public static String toJson(List<SubmissionAttachment> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return GSON.toJson(list);
    }

    public static List<SubmissionAttachment> fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<SubmissionAttachment> parsed = GSON.fromJson(json, LIST_TYPE);
            return parsed != null ? parsed : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static String describeForDisplay(SubmissionAttachment a) {
        if ("link".equals(a.kind)) {
            return "Enlace: " + a.url;
        }
        if ("photo".equals(a.kind)) {
            return "Foto: " + (a.name != null ? a.name : a.url);
        }
        if ("video".equals(a.kind)) {
            return "Video: " + (a.name != null ? a.name : a.url);
        }
        return "Archivo: " + (a.name != null ? a.name : a.url);
    }
}
