package com.aulamaestra.ui.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aulamaestra.R;
import com.aulamaestra.db.AulaRepository;
import com.aulamaestra.db.RepoCallback;
import com.aulamaestra.model.SubmissionAttachment;
import com.aulamaestra.model.SubmissionRow;
import com.aulamaestra.ui.SalonViewModel;
import com.aulamaestra.util.SubmissionAttachments;
import com.aulamaestra.util.SubmissionDisplay;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TeacherGradesFragment extends Fragment {
    private static final String ARG_SALON = "salon_id";

    private final AulaRepository repo = AulaRepository.get();
    private long salonId;
    private GradeAdapter adapter;
    private TextView empty;

    public static TeacherGradesFragment newInstance(long salonId) {
        TeacherGradesFragment f = new TeacherGradesFragment();
        Bundle b = new Bundle();
        b.putLong(ARG_SALON, salonId);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle a = getArguments();
        if (a != null) {
            salonId = a.getLong(ARG_SALON);
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
        adapter = new GradeAdapter(new ArrayList<>(), row -> showGradeDialog(row));
        rv.setAdapter(adapter);
        SalonViewModel vm = new ViewModelProvider(requireActivity()).get(SalonViewModel.class);
        vm.contentVersion.observe(getViewLifecycleOwner(), v -> load());
        load();
    }

    private void load() {
        repo.listSubmissionsForSalon(salonId, new RepoCallback<List<SubmissionRow>>() {
            @Override
            public void onSuccess(List<SubmissionRow> rows) {
                adapter.replace(rows);
                boolean isEmpty = rows.isEmpty();
                empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                empty.setText("Aún no hay entregas de tareas.");
            }

            @Override
            public void onError(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showGradeDialog(SubmissionRow row) {
        String content = SubmissionDisplay.format(row);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(row.studentName + " · " + row.assignmentTitle)
                .setMessage(content)
                .setPositiveButton(R.string.review_open_link, (d, w) -> openFirstLink(row))
                .setNeutralButton(R.string.grade, (d, w) -> showScoreDialog(row))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openFirstLink(SubmissionRow row) {
        String url = row.linkUrl;
        if (url == null || url.isEmpty()) {
            for (SubmissionAttachment a : SubmissionAttachments.fromJson(row.attachmentsJson)) {
                if ("link".equals(a.kind) && a.url != null && !a.url.isEmpty()) {
                    url = a.url;
                    break;
                }
                if (a.url != null && !a.url.isEmpty()) {
                    url = a.url;
                    break;
                }
            }
        }
        if (url == null || url.isEmpty()) {
            if (row.filePath != null && !row.filePath.isEmpty()) {
                url = row.filePath;
            }
        }
        if (url == null || url.isEmpty()) {
            Toast.makeText(requireContext(), R.string.review_no_link, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!url.startsWith("http")) {
            url = "https://" + url;
        }
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void showScoreDialog(SubmissionRow row) {
        View form = getLayoutInflater().inflate(R.layout.dialog_grade, null);
        TextInputEditText score = form.findViewById(R.id.inputScore);
        TextInputEditText feedback = form.findViewById(R.id.inputFeedback);
        if (row.score != null) {
            score.setText(String.format(Locale.getDefault(), "%.1f", row.score));
        }
        if (row.feedback != null) {
            feedback.setText(row.feedback);
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Calificar a " + row.studentName)
                .setView(form)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String s = textOf(score);
                    if (TextUtils.isEmpty(s)) {
                        Toast.makeText(requireContext(), "Escribe una calificación", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        double val = Double.parseDouble(s.replace(',', '.'));
                        repo.saveGrade(row.submissionId, val, textOf(feedback), new RepoCallback<Void>() {
                            @Override
                            public void onSuccess(Void data) {
                                new ViewModelProvider(requireActivity()).get(SalonViewModel.class).bump();
                                Toast.makeText(requireContext(), "Guardado", Toast.LENGTH_SHORT).show();
                                load();
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), "Número inválido", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String textOf(TextInputEditText e) {
        if (e.getText() == null) {
            return "";
        }
        return e.getText().toString().trim();
    }

    private interface OnPick {
        void open(SubmissionRow row);
    }

    private static class GradeAdapter extends RecyclerView.Adapter<GradeAdapter.VH> {
        private final List<SubmissionRow> data;
        private final OnPick listener;

        GradeAdapter(List<SubmissionRow> data, OnPick listener) {
            this.data = data;
            this.listener = listener;
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
            h.student.setText(r.studentName);
            h.answer.setText(SubmissionDisplay.format(r));
            if (r.score != null) {
                h.grade.setVisibility(View.VISIBLE);
                String fb = r.feedback == null ? "" : " · " + r.feedback;
                h.grade.setText(String.format(Locale.getDefault(), "Calificación: %.1f%s", r.score, fb));
            } else {
                h.grade.setVisibility(View.VISIBLE);
                h.grade.setText("Sin calificar (toca para calificar)");
            }
            h.itemView.setOnClickListener(v -> listener.open(r));
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
