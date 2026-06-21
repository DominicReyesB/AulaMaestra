package com.aulamaestra.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.aulamaestra.R;

import com.aulamaestra.db.AulaRepository;
import com.aulamaestra.db.RepoCallback;
import com.aulamaestra.model.Student;
import com.aulamaestra.ui.SalonViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class TeacherRosterFragment extends Fragment {
    private static final String ARG_SALON = "salon_id";

    private final AulaRepository repo = AulaRepository.get();
    private long salonId;
    private RosterAdapter adapter;
    private TextView empty;
    private SwipeRefreshLayout refresh;
    private View progress;

    public static TeacherRosterFragment newInstance(long salonId) {
        TeacherRosterFragment f = new TeacherRosterFragment();
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
        refresh = view.findViewById(R.id.swipeRefresh);
        progress = view.findViewById(R.id.progressLoading);
        empty = view.findViewById(R.id.textEmpty);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setHasFixedSize(true);
        adapter = new RosterAdapter(new ArrayList<>(), this::confirmDelete);
        rv.setAdapter(adapter);
        refresh.setColorSchemeResources(R.color.primary, R.color.secondary_dark);
        refresh.setOnRefreshListener(this::load);
        SalonViewModel vm = new ViewModelProvider(requireActivity()).get(SalonViewModel.class);
        vm.contentVersion.observe(getViewLifecycleOwner(), v -> load());
    }

    private void confirmDelete(Student student) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_student)
                .setMessage(getString(R.string.delete_student_confirm, student.displayName))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete_student, (dialog, which) -> deleteStudent(student))
                .show();
    }

    private void deleteStudent(Student student) {
        repo.deleteStudentFromSalon(salonId, student.id, new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                Toast.makeText(requireContext(), R.string.student_deleted, Toast.LENGTH_SHORT).show();
                load();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void load() {
        repo.listStudentsInSalon(salonId, new RepoCallback<List<Student>>() {
            @Override
            public void onSuccess(List<Student> students) {
                finishLoading();
                adapter.replace(students);
                boolean isEmpty = students.isEmpty();
                empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                empty.setText(getString(R.string.no_students));
            }

            @Override
            public void onError(String message) {
                finishLoading();
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void finishLoading() {
        if (progress != null) progress.setVisibility(View.GONE);
        if (refresh != null) refresh.setRefreshing(false);
    }

    private static class RosterAdapter extends RecyclerView.Adapter<RosterAdapter.VH> {
        interface DeleteListener {
            void onDelete(Student student);
        }

        private final List<Student> data;
        private final DeleteListener deleteListener;

        RosterAdapter(List<Student> data, DeleteListener deleteListener) {
            this.data = data;
            this.deleteListener = deleteListener;
        }

        void replace(List<Student> students) {
            data.clear();
            data.addAll(students);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_student_roster, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Student student = data.get(position);
            h.name.setText(student.displayName);
            h.delete.setOnClickListener(v -> deleteListener.onDelete(student));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView name;
            final ImageButton delete;

            VH(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.textRosterName);
                delete = itemView.findViewById(R.id.buttonDeleteStudent);
            }
        }
    }
}
