package com.aulamaestra.model;

import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;

import com.aulamaestra.R;

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

    @DrawableRes
    public static int chipBackground(int type) {
        switch (type) {
            case ASSIGNMENT:
                return R.drawable.bg_chip_assignment;
            case FILE:
                return R.drawable.bg_chip_file;
            default:
                return R.drawable.bg_chip_announcement;
        }
    }

    @ColorRes
    public static int chipTextColor(int type) {
        switch (type) {
            case ASSIGNMENT:
                return R.color.chip_assignment_text;
            case FILE:
                return R.color.chip_file_text;
            default:
                return R.color.chip_announcement_text;
        }
    }

    public static void applyChipStyle(TextView chip, int type) {
        chip.setText(label(type));
        chip.setBackgroundResource(chipBackground(type));
        chip.setTextColor(chip.getContext().getColor(chipTextColor(type)));
    }
}
