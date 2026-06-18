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
import com.aulamaestra.model.Post;
import com.aulamaestra.model.PostType;
import com.aulamaestra.ui.SalonViewModel;

import java.util.ArrayList;
import java.util.List;

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
        empty = view.findViewById(R.id.textEmpty);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new PostAdapter(new ArrayList<>());
        rv.setAdapter(adapter);
        SalonViewModel vm = new ViewModelProvider(requireActivity()).get(SalonViewModel.class);
        vm.posts.observe(getViewLifecycleOwner(), all -> {
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
            if (message != null && !message.isEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static class PostAdapter extends RecyclerView.Adapter<PostAdapter.VH> {
        private final List<Post> data;

        PostAdapter(List<Post> data) {
            this.data = data;
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
            if (p.filePath != null && !p.filePath.isEmpty()) {
                h.file.setVisibility(View.VISIBLE);
                String label = p.filePath.contains("/") ? p.filePath.substring(p.filePath.lastIndexOf('/') + 1) : p.filePath;
                h.file.setText("Archivo: " + label);
            } else {
                h.file.setVisibility(View.GONE);
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

            VH(@NonNull View itemView) {
                super(itemView);
                type = itemView.findViewById(R.id.textPostType);
                title = itemView.findViewById(R.id.textPostTitle);
                body = itemView.findViewById(R.id.textPostBody);
                file = itemView.findViewById(R.id.textPostFile);
            }
        }
    }
}
