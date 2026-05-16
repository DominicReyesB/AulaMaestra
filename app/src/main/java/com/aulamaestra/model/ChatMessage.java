package com.aulamaestra.model;

public class ChatMessage {
    public final long id;
    public final boolean fromTeacher;
    public final String body;
    public final long createdAt;

    public ChatMessage(long id, boolean fromTeacher, String body, long createdAt) {
        this.id = id;
        this.fromTeacher = fromTeacher;
        this.body = body;
        this.createdAt = createdAt;
    }
}
