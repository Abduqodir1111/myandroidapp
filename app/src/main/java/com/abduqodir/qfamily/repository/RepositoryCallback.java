package com.abduqodir.qfamily.repository;

public interface RepositoryCallback<T> {
    void onComplete(T result);
    void onError(Throwable throwable);
}

