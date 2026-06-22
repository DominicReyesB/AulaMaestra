package com.aulamaestra.model;

public class Post {
    public final long id;
    public final int type;
    public final String title;
    public final String body;
    public final String filePath;
    public final String linkUrl;
    public final long createdAt;

    public Post(long id, int type, String title, String body, String filePath, long createdAt) {
        this(id, type, title, body, filePath, null, createdAt);
    }

    public Post(long id, int type, String title, String body, String filePath, String linkUrl,
                long createdAt) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.body = body;
        this.filePath = filePath;
        this.linkUrl = linkUrl;
        this.createdAt = createdAt;
    }
}
