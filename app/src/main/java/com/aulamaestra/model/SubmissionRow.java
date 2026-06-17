package com.aulamaestra.model;

public class SubmissionRow {
    public final long submissionId;
    public final long postId;
    public final String assignmentTitle;
    public final long studentId;
    public final String studentName;
    public final String textAnswer;
    public final String filePath;
    public final String linkUrl;
    public final String attachmentsJson;
    public final long submittedAt;
    public final Double score;
    public final String feedback;

    public SubmissionRow(long submissionId, long postId, String assignmentTitle, long studentId,
                         String studentName, String textAnswer, String filePath, String linkUrl,
                         String attachmentsJson, long submittedAt, Double score, String feedback) {
        this.submissionId = submissionId;
        this.postId = postId;
        this.assignmentTitle = assignmentTitle;
        this.studentId = studentId;
        this.studentName = studentName;
        this.textAnswer = textAnswer;
        this.filePath = filePath;
        this.linkUrl = linkUrl;
        this.attachmentsJson = attachmentsJson;
        this.submittedAt = submittedAt;
        this.score = score;
        this.feedback = feedback;
    }
}
