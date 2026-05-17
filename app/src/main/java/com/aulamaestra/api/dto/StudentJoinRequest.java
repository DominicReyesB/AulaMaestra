package com.aulamaestra.api.dto;

public class StudentJoinRequest {
    public String inviteCode;
    public String displayName;
    public Long studentId;

    public StudentJoinRequest(String inviteCode, String displayName, Long studentId) {
        this.inviteCode = inviteCode;
        this.displayName = displayName;
        this.studentId = studentId;
    }
}
