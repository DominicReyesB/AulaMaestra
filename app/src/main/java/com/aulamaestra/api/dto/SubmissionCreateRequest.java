package com.aulamaestra.api.dto;

public class SubmissionCreateRequest {
    public long studentId;
    public String textAnswer;
    public String filePath;

    public SubmissionCreateRequest(long studentId, String textAnswer, String filePath) {
        this.studentId = studentId;
        this.textAnswer = textAnswer;
        this.filePath = filePath;
    }
}
