package com.aulamaestra.api.dto;

import com.google.gson.annotations.SerializedName;

public class StudentJoinRequest {
    @SerializedName("inviteCode")
    public String inviteCode;
    @SerializedName("displayName")
    public String displayName;
    @SerializedName("studentId")
    public Long studentId;
    /** "login" = ya registrado; "register" = alumno nuevo */
    @SerializedName("mode")
    public String mode;

    public StudentJoinRequest(String inviteCode, String displayName, Long studentId, String mode) {
        this.inviteCode = inviteCode;
        this.displayName = displayName;
        this.studentId = studentId;
        this.mode = mode;
    }
}
