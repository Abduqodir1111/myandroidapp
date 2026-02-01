package com.example.myappandroid.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.myappandroid.data.Person;
import com.example.myappandroid.repository.PersonRepository;
import com.example.myappandroid.repository.RepositoryCallback;

public class AddRelativeViewModel extends AndroidViewModel {
    private final PersonRepository repository;
    private final MutableLiveData<Boolean> saveResult = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public AddRelativeViewModel(@NonNull Application application) {
        super(application);
        repository = new PersonRepository(application);
    }

    public LiveData<Boolean> getSaveResult() {
        return saveResult;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void addParent(long childId, Person parent, boolean asMother) {
        repository.addParentForPerson(childId, parent, asMother, new RepositoryCallback<Long>() {
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

    public void addChild(long parentId, Person child, boolean parentIsMother) {
        repository.addChildForPerson(parentId, child, parentIsMother, new RepositoryCallback<Long>() {
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

    public void addSpouse(long personId, Person spouse) {
        repository.addSpouseForPerson(personId, spouse, new RepositoryCallback<Long>() {
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
