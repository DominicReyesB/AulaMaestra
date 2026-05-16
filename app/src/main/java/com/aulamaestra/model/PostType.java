package com.aulamaestra.model;

public final class PostType {
    public static final int ANNOUNCEMENT = 0;
    public static final int ASSIGNMENT = 1;
    public static final int FILE = 2;

    private PostType() {
    }

    public static String label(int type) {
        switch (type) {
            case ASSIGNMENT:
                return "Tarea";
            case FILE:
                return "Archivo";
            default:
                return "Anuncio";
        }
    }
}
