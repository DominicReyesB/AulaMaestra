package com.aulamaestra.api.dto;

import com.google.gson.annotations.SerializedName;

public class StudentRegisterRequest {
    @SerializedName("displayName")
    public String displayName;
    @SerializedName("password")
    public String password;
    @SerializedName("inviteCode")
    public String inviteCode;

    public StudentRegisterRequest(String displayName, String password, String inviteCode) {
        this.displayName = displayName;
        this.password = password;
        this.inviteCode = inviteCode;
    }
}
