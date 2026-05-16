package com.aulamaestra.model;

public class Salon {
    public final long id;
    public final long teacherId;
    public final int number;
    public final String inviteCode;

    public Salon(long id, long teacherId, int number, String inviteCode) {
        this.id = id;
        this.teacherId = teacherId;
        this.number = number;
        this.inviteCode = inviteCode;
    }
}
