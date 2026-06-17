package com.aulamaestra.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aulamaestra.R;
import android.widget.Toast;

import com.aulamaestra.db.AulaRepository;
import com.aulamaestra.db.RepoCallback;
import com.aulamaestra.model.SubmissionRow;
import com.aulamaestra.ui.SalonViewModel;

import com.aulamaestra.util.SubmissionDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StudentWorkFragment extends Fragment {
    private static final String ARG_SALON = "salon_id";
    private static final String ARG_STUDENT = "student_id";

    private final AulaRepository repo = AulaRepository.get();
    private long salonId;
    private long studentId;
    private WorkAdapter adapter;
    private TextView empty;

    public static StudentWorkFragment newInstance(long salonId, long studentId) {
        StudentWorkFragment f = new StudentWorkFragment();
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
        return inflater.inflate(R.layout.fragment_recycler, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView rv = view.findViewById(R.id.recycler);
        empty = view.findViewById(R.id.textEmpty);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new WorkAdapter(new ArrayList<>());
        rv.setAdapter(adapter);
        SalonViewModel vm = new ViewModelProvider(requireActivity()).get(SalonViewModel.class);
        vm.contentVersion.observe(getViewLifecycleOwner(), v -> load());
        load();
    }

    private void load() {
        repo.listSubmissionsForStudent(salonId, studentId, new RepoCallback<List<SubmissionRow>>() {
            @Override
            public void onSuccess(List<SubmissionRow> rows) {
                adapter.replace(rows);
                boolean isEmpty = rows.isEmpty();
                empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                empty.setText("Aún no has entregado tareas en este salón.");
            }

            @Override
            public void onError(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static class WorkAdapter extends RecyclerView.Adapter<WorkAdapter.VH> {
        private final List<SubmissionRow> data;

        WorkAdapter(List<SubmissionRow> data) {
            this.data = data;
        }

        void replace(List<SubmissionRow> rows) {
            data.clear();
            data.addAll(rows);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_submission, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            SubmissionRow r = data.get(position);
            h.title.setText(r.assignmentTitle);
            h.student.setVisibility(View.GONE);
            h.answer.setText(SubmissionDisplay.format(r));
            h.grade.setVisibility(View.VISIBLE);
            if (r.score != null) {
                String fb = r.feedback == null ? "" : "\n" + r.feedback;
                h.grade.setText(String.format(Locale.getDefault(), "Calificación: %.1f%s", r.score, fb));
            } else {
                h.grade.setText("Pendiente de calificación");
            }
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView student;
            final TextView answer;
            final TextView grade;

            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.textSubmissionTitle);
                student = itemView.findViewById(R.id.textStudent);
                answer = itemView.findViewById(R.id.textAnswer);
                grade = itemView.findViewById(R.id.textGrade);
            }
        }
    }
}
