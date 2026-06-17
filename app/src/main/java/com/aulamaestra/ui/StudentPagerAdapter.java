package com.aulamaestra.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.aulamaestra.ui.fragments.StudentAssignmentsFragment;
import com.aulamaestra.ui.fragments.StudentFeedFragment;
import com.aulamaestra.ui.fragments.StudentMessagesFragment;
import com.aulamaestra.ui.fragments.StudentWorkFragment;

public class StudentPagerAdapter extends FragmentStateAdapter {
    private final long salonId;
    private final long studentId;

    public StudentPagerAdapter(@NonNull FragmentActivity activity, long salonId, long studentId) {
        super(activity);
        this.salonId = salonId;
        this.studentId = studentId;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            return StudentFeedFragment.newInstance(salonId, studentId);
        }
        if (position == 1) {
            return StudentAssignmentsFragment.newInstance(salonId, studentId);
        }
        if (position == 2) {
            return StudentWorkFragment.newInstance(salonId, studentId);
        }
        return StudentMessagesFragment.newInstance(salonId, studentId);
    }

    @Override
    public int getItemCount() {
        return 4;
    }
}
