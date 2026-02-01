package com.example.myappandroid.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "people")
public class Person {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String firstName;

    public String lastName;

    public String middleName;

    public String origin;

    public Integer birthYear;

    public String photoUri;

    public Long motherId;

    public Long fatherId;

    public boolean isRoot;
}
