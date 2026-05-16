package com.aulamaestra.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.aulamaestra.R;
import com.aulamaestra.ui.MessageThreadActivity;

public class StudentMessagesFragment extends Fragment {
    private static final String ARG_SALON = "salon_id";
    private static final String ARG_STUDENT = "student_id";

    private long salonId;
    private long studentId;

    public static StudentMessagesFragment newInstance(long salonId, long studentId) {
        StudentMessagesFragment f = new StudentMessagesFragment();
        Bundle b = new Bundle();
        b.putLong(ARG_SALON, salonId);
        b.putLong(ARG_STUDENT, studentId);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle a = getArguments();
        if (a != null) {
            salonId = a.getLong(ARG_SALON);
            studentId = a.getLong(ARG_STUDENT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_student_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.btnOpenMessages).setOnClickListener(v -> {
            Intent i = new Intent(requireContext(), MessageThreadActivity.class);
            i.putExtra(MessageThreadActivity.EXTRA_SALON_ID, salonId);
            i.putExtra(MessageThreadActivity.EXTRA_STUDENT_ID, studentId);
            i.putExtra(MessageThreadActivity.EXTRA_AS_TEACHER, false);
            startActivity(i);
        });
    }
}
