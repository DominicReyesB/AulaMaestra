package com.aulamaestra.ui;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.aulamaestra.db.AulaRepository;
import com.aulamaestra.db.RepoCallback;
import com.aulamaestra.model.Post;

import java.util.ArrayList;
import java.util.List;

public class SalonViewModel extends ViewModel {
    public final MutableLiveData<Long> contentVersion = new MutableLiveData<>(0L);
    public final MutableLiveData<List<Post>> posts = new MutableLiveData<>();
    public final MutableLiveData<String> postsError = new MutableLiveData<>();

    private long salonId = -1L;
    private boolean postsLoading;
    private boolean postsLoaded;
    private boolean refreshPending;
    private long lastRefreshAt;

    public void bindSalon(AulaRepository repo, long salonId) {
        if (this.salonId == salonId && postsLoaded) {
            return;
        }
        this.salonId = salonId;
        postsLoaded = false;
        refreshPosts(repo);
    }

    public void refreshPosts(AulaRepository repo) {
        if (salonId < 0) {
            return;
        }
        if (postsLoading) {
            refreshPending = true;
            return;
        }
        postsLoading = true;
        repo.listPosts(salonId, null, new RepoCallback<List<Post>>() {
            @Override
            public void onSuccess(List<Post> list) {
                postsLoading = false;
                if (refreshPending) {
                    refreshPending = false;
                    refreshPosts(repo);
                    return;
                }
                postsLoaded = true;
                lastRefreshAt = System.currentTimeMillis();
                posts.setValue(list == null ? new ArrayList<>() : list);
            }

            @Override
            public void onError(String message) {
                postsLoading = false;
                if (refreshPending) {
                    refreshPending = false;
                    refreshPosts(repo);
                    return;
                }
                postsError.setValue(message);
            }
        });
    }

    public void refreshPostsIfStale(AulaRepository repo) {
        if (!postsLoading && System.currentTimeMillis() - lastRefreshAt > 3_000L) {
            refreshPosts(repo);
        }
    }

    public void addPost(Post post) {
        List<Post> updated = new ArrayList<>();
        updated.add(post);
        List<Post> current = posts.getValue();
        if (current != null) {
            for (Post item : current) {
                if (item.id != post.id) {
                    updated.add(item);
                }
            }
        }
        posts.setValue(updated);
    }

    public void removePost(long postId) {
        List<Post> current = posts.getValue();
        if (current == null) {
            return;
        }
        List<Post> updated = new ArrayList<>();
        for (Post item : current) {
            if (item.id != postId) {
                updated.add(item);
            }
        }
        posts.setValue(updated);
    }

    public void bump(AulaRepository repo) {
        Long v = contentVersion.getValue();
        contentVersion.setValue(v == null ? 1L : v + 1);
        postsLoaded = false;
        refreshPosts(repo);
    }
}
