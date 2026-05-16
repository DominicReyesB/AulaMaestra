package com.aulamaestra.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.aulamaestra.model.PostType;
import com.aulamaestra.ui.fragments.TeacherGradesFragment;
import com.aulamaestra.ui.fragments.TeacherMessagingFragment;
import com.aulamaestra.ui.fragments.TeacherPostsFragment;
import com.aulamaestra.ui.fragments.TeacherRosterFragment;

public class TeacherPagerAdapter extends FragmentStateAdapter {
    private final long salonId;

    public TeacherPagerAdapter(@NonNull FragmentActivity activity, long salonId) {
        super(activity);
        this.salonId = salonId;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return TeacherPostsFragment.newInstance(salonId, PostType.ANNOUNCEMENT);
            case 1:
                return TeacherPostsFragment.newInstance(salonId, PostType.ASSIGNMENT);
            case 2:
                return TeacherPostsFragment.newInstance(salonId, PostType.FILE);
            case 3:
                return TeacherRosterFragment.newInstance(salonId);
            case 4:
                return TeacherGradesFragment.newInstance(salonId);
            default:
                return TeacherMessagingFragment.newInstance(salonId);
        }
    }

    @Override
    public int getItemCount() {
        return 6;
    }
}
