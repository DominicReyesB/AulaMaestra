package com.aulamaestra.ui.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.aulamaestra.R;
import com.aulamaestra.db.AulaRepository;
import com.aulamaestra.db.RepoCallback;
import com.aulamaestra.model.Post;
import com.aulamaestra.model.PostType;
import com.aulamaestra.model.SubmissionAttachment;
import com.aulamaestra.ui.IoUtils;
import com.aulamaestra.ui.ExternalLinkUtils;
import com.aulamaestra.ui.SalonViewModel;
import com.aulamaestra.util.SubmissionAttachments;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

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
    private final Set<String> pendingAttachmentSources = new HashSet<>();
    private final Set<Long> submittedPostIds = new HashSet<>();
    private final List<Post> allAssignments = new ArrayList<>();
    private TextView attachmentsLabel;
    private View clearAttachments;
    private int pendingCopies;
    private SwipeRefreshLayout refresh;
    private View progress;

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
        refresh = view.findViewById(R.id.swipeRefresh);
        progress = view.findViewById(R.id.progressLoading);
        empty = view.findViewById(R.id.textEmpty);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setHasFixedSize(true);
        adapter = new AssignmentsAdapter(new ArrayList<>(), this::showSubmitDialog);
        rv.setAdapter(adapter);
        SalonViewModel vm = new ViewModelProvider(requireActivity()).get(SalonViewModel.class);
        refresh.setColorSchemeResources(R.color.primary, R.color.secondary_dark);
        refresh.setOnRefreshListener(() -> {
            vm.refreshPosts(repo);
            loadSubmittedAssignments();
        });
        vm.posts.observe(getViewLifecycleOwner(), posts -> applyPosts(posts == null ? new ArrayList<>() : posts));
        vm.contentVersion.observe(getViewLifecycleOwner(), v -> loadSubmittedAssignments());
        vm.postsError.observe(getViewLifecycleOwner(), message -> {
            finishLoading();
            if (message != null && !message.isEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadSubmittedAssignments() {
        repo.listSubmissionsForStudent(salonId, studentId, new RepoCallback<List<com.aulamaestra.model.SubmissionRow>>() {
            @Override
            public void onSuccess(List<com.aulamaestra.model.SubmissionRow> rows) {
                submittedPostIds.clear();
                for (com.aulamaestra.model.SubmissionRow row : rows) {
                    submittedPostIds.add(row.postId);
                }
                if (adapter != null) {
                    adapter.setSubmittedPostIds(submittedPostIds);
                }
                renderPending();
                finishLoading();
            }

            @Override
            public void onError(String message) {
                finishLoading();
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyPosts(List<Post> posts) {
        allAssignments.clear();
        for (Post p : posts) {
            if (p.type == PostType.ASSIGNMENT) {
                allAssignments.add(p);
            }
        }
        renderPending();
        if (!submittedPostIds.isEmpty() || posts.isEmpty()) finishLoading();
    }

    private void finishLoading() {
        if (progress != null) progress.setVisibility(View.GONE);
        if (refresh != null) refresh.setRefreshing(false);
    }

    private void renderPending() {
        if (adapter == null || empty == null) return;
        List<Post> pending = new ArrayList<>();
        for (Post post : allAssignments) {
            if (!submittedPostIds.contains(post.id)) pending.add(post);
        }
        adapter.replace(pending);
        empty.setVisibility(pending.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(R.string.no_assignments);
    }

    private void onPicked(Uri uri) {
        if (uri == null || pendingPickKind == null) {
            return;
        }
        String name = "adj-" + System.currentTimeMillis();
        String kind = pendingPickKind;
        pendingPickKind = null;
        String source = uri.normalizeScheme().toString();
        if (!pendingAttachmentSources.add(source)) {
            return;
        }
        pendingCopies++;
        if (attachmentsLabel != null) {
            attachmentsLabel.setText(R.string.preparing_attachment);
        }
        IoUtils.copyUriToFilesDirAsync(requireContext(), uri, name, path -> {
            pendingCopies--;
            if (!isAdded()) {
                return;
            }
            if (path == null) {
                pendingAttachmentSources.remove(source);
                Toast.makeText(requireContext(), "No se pudo leer el archivo", Toast.LENGTH_SHORT).show();
            } else if (pendingAttachmentSources.contains(source)) {
                pendingAttachments.add(new PendingAttachment(kind, path, new File(path).getName()));
            }
            refreshAttachmentLabel();
        });
    }

    private void refreshAttachmentLabel() {
        if (attachmentsLabel == null) {
            return;
        }
        if (pendingAttachments.isEmpty()) {
            attachmentsLabel.setText(R.string.submit_none_yet);
            if (clearAttachments != null) clearAttachments.setVisibility(View.GONE);
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
        if (clearAttachments != null) clearAttachments.setVisibility(View.VISIBLE);
    }

    private void showSubmitDialog(Post post) {
        if (submittedPostIds.contains(post.id)) {
            Toast.makeText(requireContext(), R.string.submit_already_sent, Toast.LENGTH_SHORT).show();
            return;
        }
        pendingAttachments.clear();
        pendingAttachmentSources.clear();
        View form = getLayoutInflater().inflate(R.layout.dialog_submit, null);
        TextInputEditText inputAnswer = form.findViewById(R.id.inputAnswer);
        TextInputEditText inputLink = form.findViewById(R.id.inputLink);
        attachmentsLabel = form.findViewById(R.id.textSubmitAttachments);
        clearAttachments = form.findViewById(R.id.btnClearAttachments);
        clearAttachments.setOnClickListener(v -> {
            pendingAttachments.clear();
            pendingAttachmentSources.clear();
            refreshAttachmentLabel();
        });
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
            if (pendingCopies > 0) {
                Toast.makeText(requireContext(), R.string.wait_for_attachment, Toast.LENGTH_SHORT).show();
                return;
            }
            String text = textOf(inputAnswer);
            String rawLink = textOf(inputLink);
            String link = rawLink.isEmpty() ? "" : ExternalLinkUtils.normalizeWebUrl(rawLink);
            if (!rawLink.isEmpty() && link == null) {
                Toast.makeText(requireContext(), R.string.invalid_link, Toast.LENGTH_SHORT).show();
                return;
            }
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
        List<SubmissionAttachment> uniqueAttachments = SubmissionAttachments.deduplicate(attachments);
        String json = SubmissionAttachments.toJson(uniqueAttachments);
        String firstFile = null;
        for (SubmissionAttachment a : uniqueAttachments) {
            if (!"link".equals(a.kind)) {
                firstFile = a.url;
                break;
            }
        }
        repo.upsertSubmission(post.id, studentId, text, firstFile, link, json, new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                submittedPostIds.add(post.id);
                if (adapter != null) {
                    adapter.setSubmittedPostIds(submittedPostIds);
                }
                renderPending();
                SalonViewModel vm = new ViewModelProvider(requireActivity()).get(SalonViewModel.class);
                vm.bump(repo);
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
        private final Set<Long> submittedPostIds = new HashSet<>();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        AssignmentsAdapter(List<Post> data, SubmitListener listener) {
            this.data = data;
            this.listener = listener;
            setHasStableIds(true);
        }

        void replace(List<Post> posts) {
            data.clear();
            data.addAll(posts);
            notifyDataSetChanged();
        }

        void setSubmittedPostIds(Set<Long> ids) {
            submittedPostIds.clear();
            submittedPostIds.addAll(ids);
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
            Linkify.addLinks(h.body, Linkify.WEB_URLS);
            h.body.setMovementMethod(LinkMovementMethod.getInstance());
            if (p.filePath != null && !p.filePath.isEmpty()) {
                h.file.setVisibility(View.VISIBLE);
                String name = new File(p.filePath).getName();
                h.file.setText(h.itemView.getContext().getString(R.string.open_named_attachment, name));
                h.file.setOnClickListener(v -> ExternalLinkUtils.open(v.getContext(), p.filePath));
            } else {
                h.file.setVisibility(View.GONE);
                h.file.setOnClickListener(null);
            }
            if (p.linkUrl != null && !p.linkUrl.isEmpty()) {
                h.link.setVisibility(View.VISIBLE);
                h.link.setOnClickListener(v -> ExternalLinkUtils.open(v.getContext(), p.linkUrl));
            } else {
                h.link.setVisibility(View.GONE);
                h.link.setOnClickListener(null);
            }
            String primaryAttachment = p.filePath != null && !p.filePath.isEmpty()
                    ? p.filePath : p.linkUrl;
            h.itemView.setOnClickListener(primaryAttachment == null || primaryAttachment.isEmpty()
                    ? null : v -> ExternalLinkUtils.open(v.getContext(), primaryAttachment));
            h.date.setText(h.itemView.getContext().getString(
                    R.string.published_at, dateFormat.format(new Date(p.createdAt))));
            h.submit.setVisibility(View.VISIBLE);
            boolean submitted = submittedPostIds.contains(p.id);
            h.submit.setEnabled(!submitted);
            if (h.submit instanceof TextView) {
                ((TextView) h.submit).setText(submitted ? R.string.submit_already_sent_short : R.string.submit);
            }
            h.submit.setOnClickListener(v -> {
                if (submittedPostIds.contains(p.id)) {
                    Toast.makeText(v.getContext(), R.string.submit_already_sent, Toast.LENGTH_SHORT).show();
                    return;
                }
                listener.onSubmit(p);
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        @Override
        public long getItemId(int position) {
            return data.get(position).id;
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView type;
            final TextView title;
            final TextView body;
            final TextView file;
            final TextView link;
            final TextView date;
            final View submit;

            VH(@NonNull View itemView) {
                super(itemView);
                type = itemView.findViewById(R.id.textPostType);
                title = itemView.findViewById(R.id.textPostTitle);
                body = itemView.findViewById(R.id.textPostBody);
                file = itemView.findViewById(R.id.textPostFile);
                link = itemView.findViewById(R.id.textPostLink);
                date = itemView.findViewById(R.id.textPostDate);
                submit = itemView.findViewById(R.id.btnSubmitAssignment);
            }
        }

    }
}
