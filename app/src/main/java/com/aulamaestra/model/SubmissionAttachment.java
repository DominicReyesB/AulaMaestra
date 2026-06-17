package com.aulamaestra.model;

public class SubmissionAttachment {
    public String kind;
    public String url;
    public String name;

    public SubmissionAttachment() {
    }

    public SubmissionAttachment(String kind, String url, String name) {
        this.kind = kind;
        this.url = url;
        this.name = name;
    }
}
