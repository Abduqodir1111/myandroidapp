package com.example.myappandroid.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.myappandroid.data.Person;
import com.example.myappandroid.repository.PersonRepository;
import com.example.myappandroid.repository.RepositoryCallback;

public class OnboardingViewModel extends AndroidViewModel {
    private final PersonRepository repository;
    private final MutableLiveData<Boolean> saveResult = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public OnboardingViewModel(@NonNull Application application) {
        super(application);
        repository = new PersonRepository(application);
    }

    public LiveData<Boolean> getSaveResult() {
        return saveResult;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void saveRootPerson(Person person) {
        repository.insertRootPerson(person, new RepositoryCallback<Long>() {
            @Override
            public void onComplete(Long result) {
                saveResult.setValue(true);
            }

            @Override
            public void onError(Throwable throwable) {
                error.setValue(throwable != null ? throwable.getMessage() : null);
                saveResult.setValue(false);
            }
        });
    }
}
