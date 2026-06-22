package com.aulamaestra.api.dto;

public class PostCreateRequest {
    public int postType;
    public String title;
    public String body;
    public String filePath;
    public String linkUrl;

    public PostCreateRequest(int postType, String title, String body, String filePath, String linkUrl) {
        this.postType = postType;
        this.title = title;
        this.body = body;
        this.filePath = filePath;
        this.linkUrl = linkUrl;
    }
}
