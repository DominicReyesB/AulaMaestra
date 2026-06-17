package com.aulamaestra.api.dto;

import com.google.gson.annotations.SerializedName;

public class SubmissionCreateRequest {
    public long studentId;
    public String textAnswer;
    public String filePath;
    @SerializedName("linkUrl")
    public String linkUrl;
    @SerializedName("attachmentsJson")
    public String attachmentsJson;

    public SubmissionCreateRequest(long studentId, String textAnswer, String filePath,
                                   String linkUrl, String attachmentsJson) {
        this.studentId = studentId;
        this.textAnswer = textAnswer;
        this.filePath = filePath;
        this.linkUrl = linkUrl;
        this.attachmentsJson = attachmentsJson;
    }
}
