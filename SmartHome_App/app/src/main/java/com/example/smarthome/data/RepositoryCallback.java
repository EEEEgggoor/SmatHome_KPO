package com.example.smarthome.data;

import androidx.annotation.NonNull;
public interface RepositoryCallback<T> {
    // Вызывается репозиторием, если операция выполнена успешно.
    void onSuccess(@NonNull T result);
    void onError(@NonNull Exception error);
}
