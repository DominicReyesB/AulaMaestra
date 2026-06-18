package com.aulamaestra.ui.fragments;

import android.os.Bundle;
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
import com.aulamaestra.model.Post;
import com.aulamaestra.model.PostType;
import com.aulamaestra.ui.SalonViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class StudentFeedFragment extends Fragment {
    private static final String ARG_SALON = "salon_id";

    private final AulaRepository repo = AulaRepository.get();
    private long salonId;
    private FeedAdapter adapter;
    private TextView empty;

    public static StudentFeedFragment newInstance(long salonId, long studentId) {
        StudentFeedFragment f = new StudentFeedFragment();
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
        adapter = new FeedAdapter(new ArrayList<>());
        rv.setAdapter(adapter);
        SalonViewModel vm = new ViewModelProvider(requireActivity()).get(SalonViewModel.class);
        vm.posts.observe(getViewLifecycleOwner(), posts -> applyPosts(posts == null ? new ArrayList<>() : posts));
        vm.postsError.observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyPosts(List<Post> posts) {
        List<Post> filtered = new ArrayList<>();
        for (Post p : posts) {
            if (p.type != PostType.ASSIGNMENT) {
                filtered.add(p);
            }
        }
        adapter.replace(filtered);
        empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(R.string.no_classroom_posts);
    }

    private static class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.VH> {
        private final List<Post> data;

        FeedAdapter(List<Post> data) {
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
                h.file.setText("Archivo: " + new File(p.filePath).getName());
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
