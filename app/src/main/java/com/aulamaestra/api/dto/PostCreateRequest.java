package com.aulamaestra.api.dto;

public class PostCreateRequest {
    public int postType;
    public String title;
    public String body;
    public String filePath;

    public PostCreateRequest(int postType, String title, String body, String filePath) {
        this.postType = postType;
        this.title = title;
        this.body = body;
        this.filePath = filePath;
    }
}
