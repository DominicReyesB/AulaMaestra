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

    public void bindSalon(AulaRepository repo, long salonId) {
        if (this.salonId == salonId && postsLoaded) {
            return;
        }
        this.salonId = salonId;
        postsLoaded = false;
        refreshPosts(repo);
    }

    public void refreshPosts(AulaRepository repo) {
        if (salonId < 0 || postsLoading) {
            return;
        }
        postsLoading = true;
        repo.listPosts(salonId, null, new RepoCallback<List<Post>>() {
            @Override
            public void onSuccess(List<Post> list) {
                postsLoading = false;
                postsLoaded = true;
                posts.setValue(list == null ? new ArrayList<>() : list);
            }

            @Override
            public void onError(String message) {
                postsLoading = false;
                postsError.setValue(message);
            }
        });
    }

    public void bump(AulaRepository repo) {
        Long v = contentVersion.getValue();
        contentVersion.setValue(v == null ? 1L : v + 1);
        postsLoaded = false;
        refreshPosts(repo);
    }
}
