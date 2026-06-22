package com.aulamaestra.util;

import com.aulamaestra.model.SubmissionAttachment;
import com.aulamaestra.model.SubmissionRow;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SubmissionDisplay {
    private SubmissionDisplay() {
    }

    public static String format(SubmissionRow r) {
        StringBuilder sb = new StringBuilder();
        if (r.textAnswer != null && !r.textAnswer.isEmpty()) {
            sb.append(r.textAnswer);
        }
        Set<String> shownUrls = new HashSet<>();
        if (r.linkUrl != null && !r.linkUrl.isEmpty()) {
            String cleanLink = r.linkUrl.trim();
            if (shownUrls.add(cleanLink)) {
                appendLine(sb, "Enlace: " + cleanLink);
            }
        }
        List<SubmissionAttachment> attachments = SubmissionAttachments.fromJson(r.attachmentsJson);
        for (SubmissionAttachment a : attachments) {
            if (shownUrls.add(a.url.trim())) {
                appendLine(sb, SubmissionAttachments.describeForDisplay(a));
            }
        }
        if (r.filePath != null && !r.filePath.isEmpty()) {
            String cleanFile = r.filePath.trim();
            if (shownUrls.add(cleanFile)) {
                appendLine(sb, "Archivo: " + new File(cleanFile).getName());
            }
        }
        return sb.length() == 0 ? "(sin contenido)" : sb.toString();
    }

    private static void appendLine(StringBuilder sb, String line) {
        if (sb.length() > 0) {
            sb.append("\n");
        }
        sb.append(line);
    }
}
