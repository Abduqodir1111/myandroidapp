package com.example.myappandroid.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.example.myappandroid.data.AppDatabase;
import com.example.myappandroid.data.Person;
import java.util.List;

public class PersonRepository {
    private final AppDatabase database;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public PersonRepository(Context context) {
        database = AppDatabase.getInstance(context.getApplicationContext());
    }

    public void insertRootPerson(Person person, RepositoryCallback<Long> callback) {
        AppDatabase.getDatabaseExecutor().execute(() -> {
            try {
                database.personDao().clearRoot();
                person.isRoot = true;
                long now = System.currentTimeMillis();
                person.createdAt = now;
                person.updatedAt = now;
                long id = database.personDao().insert(person);
                postSuccess(callback, id);
            } catch (Throwable t) {
                postError(callback, t);
            }
        });
    }

    public void insertPerson(Person person, RepositoryCallback<Long> callback) {
        AppDatabase.getDatabaseExecutor().execute(() -> {
            try {
                long now = System.currentTimeMillis();
                person.createdAt = now;
                person.updatedAt = now;
                long id = database.personDao().insert(person);
                postSuccess(callback, id);
            } catch (Throwable t) {
                postError(callback, t);
            }
        });
    }

    public void updatePerson(Person person) {
        updatePerson(person, null);
    }

    public void updatePerson(Person person, RepositoryCallback<Boolean> callback) {
        AppDatabase.getDatabaseExecutor().execute(() -> {
            try {
                person.updatedAt = System.currentTimeMillis();
                database.personDao().update(person);
                postSuccess(callback, true);
            } catch (Throwable t) {
                postError(callback, t);
            }
        });
    }

    public void getRootPerson(RepositoryCallback<Person> callback) {
        AppDatabase.getDatabaseExecutor().execute(() -> {
            try {
                Person person = database.personDao().getRootPerson();
                postSuccess(callback, person);
            } catch (Throwable t) {
                postError(callback, t);
            }
        });
    }

    public void getPersonById(long personId, RepositoryCallback<Person> callback) {
        AppDatabase.getDatabaseExecutor().execute(() -> {
            try {
                Person person = database.personDao().getById(personId);
                postSuccess(callback, person);
            } catch (Throwable t) {
                postError(callback, t);
            }
        });
    }

    public void getAllPeople(RepositoryCallback<List<Person>> callback) {
        AppDatabase.getDatabaseExecutor().execute(() -> {
            try {
                List<Person> people = database.personDao().getAll();
                postSuccess(callback, people);
            } catch (Throwable t) {
                postError(callback, t);
            }
        });
    }

    public void addParentForPerson(long childId,
                                   Person parent,
                                   boolean asMother,
                                   RepositoryCallback<Long> callback) {
        AppDatabase.getDatabaseExecutor().execute(() -> {
            try {
                long now = System.currentTimeMillis();
                parent.createdAt = now;
                parent.updatedAt = now;
                long parentId = database.personDao().insert(parent);
                Person child = database.personDao().getById(childId);
                if (child != null) {
                    if (asMother) {
                        child.motherId = parentId;
                    } else {
                        child.fatherId = parentId;
                    }
                    child.updatedAt = now;
                    database.personDao().update(child);
                }
                postSuccess(callback, parentId);
            } catch (Throwable t) {
                postError(callback, t);
            }
        });
    }

    public void addChildForPerson(long parentId,
                                  Person child,
                                  boolean parentIsMother,
                                  RepositoryCallback<Long> callback) {
        AppDatabase.getDatabaseExecutor().execute(() -> {
            try {
                long now = System.currentTimeMillis();
                child.createdAt = now;
                child.updatedAt = now;
                if (parentIsMother) {
                    child.motherId = parentId;
                } else {
                    child.fatherId = parentId;
                }
                long childId = database.personDao().insert(child);
                postSuccess(callback, childId);
            } catch (Throwable t) {
                postError(callback, t);
            }
        });
    }

    public void addSpouseForPerson(long personId,
                                   Person spouse,
                                   RepositoryCallback<Long> callback) {
        AppDatabase.getDatabaseExecutor().execute(() -> {
            try {
                long now = System.currentTimeMillis();
                spouse.createdAt = now;
                spouse.updatedAt = now;
                spouse.spouseId = personId;
                long spouseId = database.personDao().insert(spouse);

                Person person = database.personDao().getById(personId);
                if (person != null) {
                    person.spouseId = spouseId;
                    person.updatedAt = now;
                    database.personDao().update(person);
                }
                postSuccess(callback, spouseId);
            } catch (Throwable t) {
                postError(callback, t);
            }
        });
    }

    private <T> void postSuccess(RepositoryCallback<T> callback, T result) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onComplete(result));
    }

    private void postError(RepositoryCallback<?> callback, Throwable throwable) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onError(throwable));
    }
}
