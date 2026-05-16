package com.aulamaestra.api.dto;

public class MessageRequest {
    public boolean fromTeacher;
    public String body;

    public MessageRequest(boolean fromTeacher, String body) {
        this.fromTeacher = fromTeacher;
        this.body = body;
    }
}
