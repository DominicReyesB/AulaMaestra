package com.aulamaestra.model;

public class StudentJoinResult {
    public final long studentId;
    public final long salonId;
    public final String displayName;
    public final int salonNumber;

    public StudentJoinResult(long studentId, long salonId, String displayName, int salonNumber) {
        this.studentId = studentId;
        this.salonId = salonId;
        this.displayName = displayName;
        this.salonNumber = salonNumber;
    }
}
