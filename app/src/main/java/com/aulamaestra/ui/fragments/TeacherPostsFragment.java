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
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.aulamaestra.R;
import android.widget.Toast;

import com.aulamaestra.db.AulaRepository;
import com.aulamaestra.db.RepoCallback;
import com.aulamaestra.model.Post;
import com.aulamaestra.model.PostType;
import com.aulamaestra.ui.SalonViewModel;
import com.aulamaestra.ui.ExternalLinkUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TeacherPostsFragment extends Fragment {
    private static final String ARG_SALON = "salon_id";
    private static final String ARG_TYPE = "post_type";

    private final AulaRepository repo = AulaRepository.get();
    private long salonId;
    private int postType;
    private PostAdapter adapter;
    private TextView empty;

    public static TeacherPostsFragment newInstance(long salonId, int postType) {
        TeacherPostsFragment f = new TeacherPostsFragment();
        Bundle b = new Bundle();
        b.putLong(ARG_SALON, salonId);
        b.putInt(ARG_TYPE, postType);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle a = getArguments();
        if (a != null) {
            salonId = a.getLong(ARG_SALON);
            postType = a.getInt(ARG_TYPE);
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
        SwipeRefreshLayout refresh = view.findViewById(R.id.swipeRefresh);
        View progress = view.findViewById(R.id.progressLoading);
        empty = view.findViewById(R.id.textEmpty);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setHasFixedSize(true);
        adapter = new PostAdapter(new ArrayList<>(), new PostAdapter.Listener() {
            @Override
            public void onOpen(Post post) {
                openAttachment(post.filePath);
            }

            @Override
            public void onDelete(Post post) {
                confirmDelete(post);
            }
        });
        rv.setAdapter(adapter);
        SalonViewModel vm = new ViewModelProvider(requireActivity()).get(SalonViewModel.class);
        refresh.setColorSchemeResources(R.color.primary, R.color.secondary_dark);
        refresh.setOnRefreshListener(() -> vm.refreshPosts(repo));
        vm.posts.observe(getViewLifecycleOwner(), all -> {
            progress.setVisibility(View.GONE);
            refresh.setRefreshing(false);
            List<Post> filtered = new ArrayList<>();
            if (all != null) {
                for (Post p : all) {
                    if (p.type == postType) {
                        filtered.add(p);
                    }
                }
            }
            adapter.replace(filtered);
            boolean isEmpty = filtered.isEmpty();
            empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            empty.setText("No hay publicaciones todavía.");
        });
        vm.postsError.observe(getViewLifecycleOwner(), message -> {
            progress.setVisibility(View.GONE);
            refresh.setRefreshing(false);
            if (message != null && !message.isEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openAttachment(String url) {
        ExternalLinkUtils.open(requireContext(), url);
    }

    private void confirmDelete(Post post) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_post)
                .setMessage(R.string.delete_post_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete_post, (d, w) -> repo.deletePost(post.id, new RepoCallback<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        new ViewModelProvider(requireActivity()).get(SalonViewModel.class).bump(repo);
                        Toast.makeText(requireContext(), R.string.post_deleted, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    }
                }))
                .show();
    }

    private static class PostAdapter extends RecyclerView.Adapter<PostAdapter.VH> {
        interface Listener {
            void onOpen(Post post);
            void onDelete(Post post);
        }

        private final List<Post> data;
        private final Listener listener;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        PostAdapter(List<Post> data, Listener listener) {
            this.data = data;
            this.listener = listener;
            setHasStableIds(true);
        }

        void replace(List<Post> posts) {
            data.clear();
            data.addAll(posts);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post, parent, false);
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
                String label = p.filePath.contains("/") ? p.filePath.substring(p.filePath.lastIndexOf('/') + 1) : p.filePath;
                h.file.setText(h.itemView.getContext().getString(R.string.open_named_attachment, label));
                h.file.setOnClickListener(v -> listener.onOpen(p));
                h.itemView.setOnClickListener(v -> listener.onOpen(p));
            } else {
                h.file.setVisibility(View.GONE);
                h.file.setOnClickListener(null);
                h.itemView.setOnClickListener(null);
            }
            h.date.setText(h.itemView.getContext().getString(
                    R.string.published_at, dateFormat.format(new Date(p.createdAt))));
            h.delete.setVisibility(View.VISIBLE);
            h.delete.setOnClickListener(v -> listener.onDelete(p));
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
            final TextView date;
            final ImageButton delete;

            VH(@NonNull View itemView) {
                super(itemView);
                type = itemView.findViewById(R.id.textPostType);
                title = itemView.findViewById(R.id.textPostTitle);
                body = itemView.findViewById(R.id.textPostBody);
                file = itemView.findViewById(R.id.textPostFile);
                date = itemView.findViewById(R.id.textPostDate);
                delete = itemView.findViewById(R.id.buttonDeletePost);
            }
        }
    }
}
