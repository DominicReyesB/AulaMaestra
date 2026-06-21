package com.aulamaestra.api.dto;

import com.google.gson.annotations.SerializedName;

public class HelpQuestionRequest {
    @SerializedName("question")
    public final String question;

    public HelpQuestionRequest(String question) {
        this.question = question;
    }
}
