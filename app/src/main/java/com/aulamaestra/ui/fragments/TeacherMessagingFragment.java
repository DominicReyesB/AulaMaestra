package com.aulamaestra.ui.fragments;

import android.content.Intent;
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
import com.aulamaestra.model.Student;
import com.aulamaestra.ui.MessageThreadActivity;
import com.aulamaestra.ui.SalonViewModel;

import java.util.ArrayList;
import java.util.List;

public class TeacherMessagingFragment extends Fragment {
    private static final String ARG_SALON = "salon_id";

    private final AulaRepository repo = AulaRepository.get();
    private long salonId;
    private MsgStudentAdapter adapter;
    private TextView empty;

    public static TeacherMessagingFragment newInstance(long salonId) {
        TeacherMessagingFragment f = new TeacherMessagingFragment();
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
        adapter = new MsgStudentAdapter(new ArrayList<>(), (student) -> {
            Intent i = new Intent(requireContext(), MessageThreadActivity.class);
            i.putExtra(MessageThreadActivity.EXTRA_SALON_ID, salonId);
            i.putExtra(MessageThreadActivity.EXTRA_STUDENT_ID, student.id);
            i.putExtra(MessageThreadActivity.EXTRA_AS_TEACHER, true);
            startActivity(i);
        });
        rv.setAdapter(adapter);
        SalonViewModel vm = new ViewModelProvider(requireActivity()).get(SalonViewModel.class);
        vm.contentVersion.observe(getViewLifecycleOwner(), v -> load());
        load();
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        repo.listStudentsInSalon(salonId, new RepoCallback<List<Student>>() {
            @Override
            public void onSuccess(List<Student> students) {
                adapter.replace(students);
                boolean isEmpty = students.isEmpty();
                empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                empty.setText(getString(R.string.no_students));
            }

            @Override
            public void onError(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private interface OpenChat {
        void onOpen(Student s);
    }

    private static class MsgStudentAdapter extends RecyclerView.Adapter<MsgStudentAdapter.VH> {
        private final List<Student> data;
        private final OpenChat openChat;

        MsgStudentAdapter(List<Student> data, OpenChat openChat) {
            this.data = data;
            this.openChat = openChat;
        }

        void replace(List<Student> students) {
            data.clear();
            data.addAll(students);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_student, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Student s = data.get(position);
            h.name.setText(s.displayName);
            h.chat.setOnClickListener(v -> openChat.onOpen(s));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView name;
            final View chat;

            VH(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.textStudentName);
                chat = itemView.findViewById(R.id.btnChat);
            }
        }
    }
}
