package com.example.myappandroid.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface PersonDao {
    @Insert
    long insert(Person person);

    @Update
    void update(Person person);

    @Query("SELECT * FROM people ORDER BY lastName COLLATE NOCASE, firstName COLLATE NOCASE")
    List<Person> getAll();

    @Query("SELECT * FROM people WHERE id = :id LIMIT 1")
    Person getById(long id);

    @Query("SELECT * FROM people WHERE isRoot = 1 LIMIT 1")
    Person getRootPerson();

    @Query("UPDATE people SET isRoot = 0")
    void clearRoot();

    @Query("SELECT * FROM people WHERE id IN (SELECT motherId FROM people WHERE id = :personId) " +
            "OR id IN (SELECT fatherId FROM people WHERE id = :personId)")
    List<Person> getParents(long personId);

    @Query("SELECT * FROM people WHERE motherId = :personId OR fatherId = :personId")
    List<Person> getChildren(long personId);

    @Query("DELETE FROM people WHERE id = :id")
    void deleteById(long id);
}
