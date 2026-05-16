package com.aulamaestra.ui.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aulamaestra.R;
import com.aulamaestra.db.AulaRepository;
import com.aulamaestra.db.RepoCallback;
import com.aulamaestra.model.Post;
import com.aulamaestra.model.PostType;
import com.aulamaestra.ui.IoUtils;
import com.aulamaestra.ui.SalonViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class StudentFeedFragment extends Fragment {
    private static final String ARG_SALON = "salon_id";
    private static final String ARG_STUDENT = "student_id";

    private final AulaRepository repo = AulaRepository.get();
    private long salonId;
    private long studentId;
    private FeedAdapter adapter;
    private TextView empty;
    private ActivityResultLauncher<String> pickFile;
    private final AtomicReference<Uri> pendingSubmitUri = new AtomicReference<>();
    private TextView activeSubmitFileLabel;

    public static StudentFeedFragment newInstance(long salonId, long studentId) {
        StudentFeedFragment f = new StudentFeedFragment();
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
        pickFile = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            pendingSubmitUri.set(uri);
            if (activeSubmitFileLabel != null) {
                activeSubmitFileLabel.setText(uri == null ? "" : "Archivo seleccionado");
            }
        });
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
        adapter = new FeedAdapter(new ArrayList<>(), post -> showSubmitDialog(post));
        rv.setAdapter(adapter);
        SalonViewModel vm = new ViewModelProvider(requireActivity()).get(SalonViewModel.class);
        vm.contentVersion.observe(getViewLifecycleOwner(), v -> load());
        load();
    }

    private void load() {
        repo.listPosts(salonId, null, new RepoCallback<List<Post>>() {
            @Override
            public void onSuccess(List<Post> posts) {
                adapter.replace(posts);
                boolean isEmpty = posts.isEmpty();
                empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                empty.setText("Tu maestra aún no ha publicado nada en este salón.");
            }

            @Override
            public void onError(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showSubmitDialog(Post post) {
        pendingSubmitUri.set(null);
        View form = getLayoutInflater().inflate(R.layout.dialog_submit, null);
        TextInputEditText input = form.findViewById(R.id.inputAnswer);
        TextView fileLabel = form.findViewById(R.id.textSubmitFile);
        form.findViewById(R.id.btnPickSubmitFile).setOnClickListener(v -> {
            activeSubmitFileLabel = fileLabel;
            pickFile.launch("*/*");
        });
        AlertDialog d = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Entregar: " + post.title)
                .setView(form)
                .setPositiveButton(R.string.submit, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = input.getText() == null ? "" : input.getText().toString().trim();
            Uri u = pendingSubmitUri.get();
            String path = null;
            if (u != null) {
                String name = "entrega-" + post.id + "-" + System.currentTimeMillis();
                path = IoUtils.copyUriToFilesDir(requireContext(), u, name);
            }
            if (text.isEmpty() && path == null) {
                Toast.makeText(requireContext(), "Escribe algo o adjunta un archivo", Toast.LENGTH_SHORT).show();
                return;
            }
            submitWork(post, text, path, d);
        });
    }

    private void submitWork(Post post, String text, String localPath, AlertDialog d) {
        if (localPath != null) {
            repo.uploadLocalFile(localPath, new RepoCallback<String>() {
                @Override
                public void onSuccess(String url) {
                    saveSubmission(post, studentId, text, url, d);
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            saveSubmission(post, studentId, text, null, d);
        }
    }

    private void saveSubmission(Post post, long studentId, String text, String fileUrl, AlertDialog d) {
        repo.upsertSubmission(post.id, studentId, text, fileUrl, new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                new ViewModelProvider(requireActivity()).get(SalonViewModel.class).bump();
                Toast.makeText(requireContext(), "Entrega registrada", Toast.LENGTH_SHORT).show();
                d.dismiss();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private interface SubmitListener {
        void onSubmit(Post post);
    }

    private static class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.VH> {
        private final List<Post> data;
        private final SubmitListener listener;

        FeedAdapter(List<Post> data, SubmitListener listener) {
            this.data = data;
            this.listener = listener;
        }

        void replace(List<Post> posts) {
            data.clear();
            data.addAll(posts);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post_student, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Post p = data.get(position);
            h.type.setText(PostType.label(p.type));
            h.title.setText(p.title);
            h.body.setText(p.body == null ? "" : p.body);
            if (p.filePath != null && !p.filePath.isEmpty()) {
                h.file.setVisibility(View.VISIBLE);
                h.file.setText("Archivo: " + new File(p.filePath).getName());
            } else {
                h.file.setVisibility(View.GONE);
            }
            if (p.type == PostType.ASSIGNMENT) {
                h.submit.setVisibility(View.VISIBLE);
                h.submit.setOnClickListener(v -> listener.onSubmit(p));
            } else {
                h.submit.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView type;
            final TextView title;
            final TextView body;
            final TextView file;
            final View submit;

            VH(@NonNull View itemView) {
                super(itemView);
                type = itemView.findViewById(R.id.textPostType);
                title = itemView.findViewById(R.id.textPostTitle);
                body = itemView.findViewById(R.id.textPostBody);
                file = itemView.findViewById(R.id.textPostFile);
                submit = itemView.findViewById(R.id.btnSubmitAssignment);
            }
        }
    }
}
