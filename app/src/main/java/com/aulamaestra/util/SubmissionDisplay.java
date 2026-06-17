package com.aulamaestra.util;

import com.aulamaestra.model.SubmissionAttachment;
import com.aulamaestra.model.SubmissionRow;

import java.io.File;
import java.util.List;

public final class SubmissionDisplay {
    private SubmissionDisplay() {
    }

    public static String format(SubmissionRow r) {
        StringBuilder sb = new StringBuilder();
        if (r.textAnswer != null && !r.textAnswer.isEmpty()) {
            sb.append(r.textAnswer);
        }
        if (r.linkUrl != null && !r.linkUrl.isEmpty()) {
            appendLine(sb, "Enlace: " + r.linkUrl);
        }
        List<SubmissionAttachment> attachments = SubmissionAttachments.fromJson(r.attachmentsJson);
        for (SubmissionAttachment a : attachments) {
            appendLine(sb, SubmissionAttachments.describeForDisplay(a));
        }
        if (attachments.isEmpty() && r.filePath != null && !r.filePath.isEmpty()) {
            appendLine(sb, "Archivo: " + new File(r.filePath).getName());
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
