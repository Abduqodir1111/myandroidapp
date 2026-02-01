package com.example.myappandroid.repository;

public interface RepositoryCallback<T> {
    void onComplete(T result);
    void onError(Throwable throwable);
}
