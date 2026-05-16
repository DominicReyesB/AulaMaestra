package com.aulamaestra.api.dto;

public class GradeRequest {
    public double score;
    public String feedback;

    public GradeRequest(double score, String feedback) {
        this.score = score;
        this.feedback = feedback;
    }
}
