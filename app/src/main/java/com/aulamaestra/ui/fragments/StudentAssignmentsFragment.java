package com.aulamaestra.ui.fragments;

import android.os.Bundle;
import android.text.TextUtils;
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
import com.aulamaestra.model.SubmissionAttachment;
import com.aulamaestra.ui.IoUtils;
import com.aulamaestra.ui.SalonViewModel;
import com.aulamaestra.util.SubmissionAttachments;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class StudentAssignmentsFragment extends Fragment {
    private static final String ARG_SALON = "salon_id";
    private static final String ARG_STUDENT = "student_id";

    private final AulaRepository repo = AulaRepository.get();
    private long salonId;
    private long studentId;
    private AssignmentsAdapter adapter;
    private TextView empty;
    private ActivityResultLauncher<String> pickPhoto;
    private ActivityResultLauncher<String> pickVideo;
    private ActivityResultLauncher<String> pickFile;
    private String pendingPickKind;
    private final List<PendingAttachment> pendingAttachments = new ArrayList<>();
    private TextView attachmentsLabel;

    public static StudentAssignmentsFragment newInstance(long salonId, long studentId) {
        StudentAssignmentsFragment f = new StudentAssignmentsFragment();
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
        pickPhoto = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPicked);
        pickVideo = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPicked);
        pickFile = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPicked);
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
        adapter = new AssignmentsAdapter(new ArrayList<>(), this::showSubmitDialog);
        rv.setAdapter(adapter);
        SalonViewModel vm = new ViewModelProvider(requireActivity()).get(SalonViewModel.class);
        vm.contentVersion.observe(getViewLifecycleOwner(), v -> load());
        load();
    }

    private void load() {
        repo.listPosts(salonId, PostType.ASSIGNMENT, new RepoCallback<List<Post>>() {
            @Override
            public void onSuccess(List<Post> posts) {
                adapter.replace(posts);
                empty.setVisibility(posts.isEmpty() ? View.VISIBLE : View.GONE);
                empty.setText(R.string.no_assignments);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onPicked(Uri uri) {
        if (uri == null || pendingPickKind == null) {
            return;
        }
        String name = "adj-" + System.currentTimeMillis();
        String path = IoUtils.copyUriToFilesDir(requireContext(), uri, name);
        if (path == null) {
            Toast.makeText(requireContext(), "No se pudo leer el archivo", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingAttachments.add(new PendingAttachment(pendingPickKind, path, new File(path).getName()));
        refreshAttachmentLabel();
    }

    private void refreshAttachmentLabel() {
        if (attachmentsLabel == null) {
            return;
        }
        if (pendingAttachments.isEmpty()) {
            attachmentsLabel.setText(R.string.submit_none_yet);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (PendingAttachment p : pendingAttachments) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("• ").append(p.displayName);
        }
        attachmentsLabel.setText(sb.toString());
    }

    private void showSubmitDialog(Post post) {
        pendingAttachments.clear();
        View form = getLayoutInflater().inflate(R.layout.dialog_submit, null);
        TextInputEditText inputAnswer = form.findViewById(R.id.inputAnswer);
        TextInputEditText inputLink = form.findViewById(R.id.inputLink);
        attachmentsLabel = form.findViewById(R.id.textSubmitAttachments);
        refreshAttachmentLabel();

        form.findViewById(R.id.btnPickPhoto).setOnClickListener(v -> {
            pendingPickKind = "photo";
            pickPhoto.launch("image/*");
        });
        form.findViewById(R.id.btnPickVideo).setOnClickListener(v -> {
            pendingPickKind = "video";
            pickVideo.launch("video/*");
        });
        form.findViewById(R.id.btnPickFile).setOnClickListener(v -> {
            pendingPickKind = "file";
            pickFile.launch("*/*");
        });

        AlertDialog d = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.submit_task_title, post.title))
                .setView(form)
                .setPositiveButton(R.string.submit, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = textOf(inputAnswer);
            String link = textOf(inputLink);
            if (text.isEmpty() && link.isEmpty() && pendingAttachments.isEmpty()) {
                Toast.makeText(requireContext(), R.string.submit_need_content, Toast.LENGTH_SHORT).show();
                return;
            }
            uploadAllAndSubmit(post, text, link, d);
        });
    }

    private void uploadAllAndSubmit(Post post, String text, String link, AlertDialog d) {
        if (pendingAttachments.isEmpty()) {
            saveSubmission(post, text, link, new ArrayList<>(), d);
            return;
        }
        List<SubmissionAttachment> uploaded = new ArrayList<>();
        uploadNext(post, text, link, d, uploaded, 0);
    }

    private void uploadNext(Post post, String text, String link, AlertDialog d,
                            List<SubmissionAttachment> uploaded, int index) {
        if (index >= pendingAttachments.size()) {
            saveSubmission(post, text, link, uploaded, d);
            return;
        }
        PendingAttachment p = pendingAttachments.get(index);
        repo.uploadLocalFile(p.localPath, new RepoCallback<String>() {
            @Override
            public void onSuccess(String url) {
                uploaded.add(new SubmissionAttachment(p.kind, url, p.displayName));
                uploadNext(post, text, link, d, uploaded, index + 1);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveSubmission(Post post, String text, String link,
                                List<SubmissionAttachment> attachments, AlertDialog d) {
        List<SubmissionAttachment> all = new ArrayList<>(attachments);
        if (!TextUtils.isEmpty(link)) {
            all.add(new SubmissionAttachment("link", link.trim(), link.trim()));
        }
        String json = SubmissionAttachments.toJson(all.isEmpty() ? null : all);
        String firstFile = null;
        for (SubmissionAttachment a : attachments) {
            if (!"link".equals(a.kind)) {
                firstFile = a.url;
                break;
            }
        }
        repo.upsertSubmission(post.id, studentId, text, firstFile, link, json, new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                new ViewModelProvider(requireActivity()).get(SalonViewModel.class).bump();
                Toast.makeText(requireContext(), R.string.submit_ok, Toast.LENGTH_SHORT).show();
                d.dismiss();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static String textOf(TextInputEditText e) {
        if (e.getText() == null) {
            return "";
        }
        return e.getText().toString().trim();
    }

    private interface SubmitListener {
        void onSubmit(Post post);
    }

    private static class PendingAttachment {
        final String kind;
        final String localPath;
        final String displayName;

        PendingAttachment(String kind, String localPath, String displayName) {
            this.kind = kind;
            this.localPath = localPath;
            this.displayName = displayName;
        }
    }

    private static class AssignmentsAdapter extends RecyclerView.Adapter<AssignmentsAdapter.VH> {
        private final List<Post> data;
        private final SubmitListener listener;

        AssignmentsAdapter(List<Post> data, SubmitListener listener) {
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
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_post_student, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Post p = data.get(position);
            PostType.applyChipStyle(h.type, p.type);
            h.title.setText(p.title);
            h.body.setText(p.body == null ? "" : p.body);
            if (p.filePath != null && !p.filePath.isEmpty()) {
                h.file.setVisibility(View.VISIBLE);
                h.file.setText("Material: " + new File(p.filePath).getName());
            } else {
                h.file.setVisibility(View.GONE);
            }
            h.submit.setVisibility(View.VISIBLE);
            h.submit.setOnClickListener(v -> listener.onSubmit(p));
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
