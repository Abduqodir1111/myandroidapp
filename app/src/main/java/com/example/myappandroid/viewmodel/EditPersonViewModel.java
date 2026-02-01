package com.example.myappandroid.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.myappandroid.data.Person;
import com.example.myappandroid.repository.PersonRepository;
import com.example.myappandroid.repository.RepositoryCallback;

public class EditPersonViewModel extends AndroidViewModel {
    private final PersonRepository repository;
    private final MutableLiveData<Person> person = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saveResult = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public EditPersonViewModel(@NonNull Application application) {
        super(application);
        repository = new PersonRepository(application);
    }

    public LiveData<Person> getPerson() {
        return person;
    }

    public LiveData<Boolean> getSaveResult() {
        return saveResult;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void loadPerson(long personId) {
        repository.getPersonById(personId, new RepositoryCallback<Person>() {
            @Override
            public void onComplete(Person result) {
                person.setValue(result);
            }

            @Override
            public void onError(Throwable throwable) {
                error.setValue(throwable != null ? throwable.getMessage() : null);
            }
        });
    }

    public void updatePerson(Person updated) {
        repository.updatePerson(updated, new RepositoryCallback<Boolean>() {
            @Override
            public void onComplete(Boolean result) {
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
