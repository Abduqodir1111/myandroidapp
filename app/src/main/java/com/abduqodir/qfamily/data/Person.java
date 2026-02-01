package com.abduqodir.qfamily.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "people")
public class Person {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String firstName;

    @NonNull
    public String lastName;

    @NonNull
    public String middleName;

    public Long birthDate;

    public String photoUri;

    public String gender;

    public Long fatherId;

    public Long motherId;

    public Long spouseId;

    public Long createdAt;

    public Long updatedAt;

    public boolean isRoot;
}

