package com.aulamaestra.api.dto;

import com.google.gson.annotations.SerializedName;

public class SubmissionDto {
    public long submissionId;
    public long postId;
    public String assignmentTitle;
    public long studentId;
    public String studentName;
    public String textAnswer;
    public String filePath;
    @SerializedName("linkUrl")
    public String linkUrl;
    @SerializedName("attachmentsJson")
    public String attachmentsJson;
    public long submittedAt;
    public Double score;
    public String feedback;
}
