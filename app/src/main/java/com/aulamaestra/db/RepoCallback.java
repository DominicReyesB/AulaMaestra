package com.aulamaestra.db;

public interface RepoCallback<T> {
    void onSuccess(T data);

    void onError(String message);
}
