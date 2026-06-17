package com.aulamaestra.api.dto;

import com.google.gson.annotations.SerializedName;

public class AuthLoginResponse {
    @SerializedName("role")
    public String role;
    @SerializedName("teacherId")
    public Long teacherId;
    @SerializedName("studentId")
    public Long studentId;
    @SerializedName("displayName")
    public String displayName;
    @SerializedName("salonId")
    public Long salonId;
    @SerializedName("salonNumber")
    public Integer salonNumber;
}
