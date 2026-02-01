package com.example.myappandroid;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.example.myappandroid.data.AppDatabase;
import com.example.myappandroid.data.Person;
import java.util.ArrayList;
import java.util.List;

public class AddPersonActivity extends AppCompatActivity {
    private AppDatabase database;
    private EditText editFirstName;
    private EditText editLastName;
    private EditText editMiddleName;
    private EditText editBirthYear;
    private EditText editOrigin;
    private TextView textAge;
    private Spinner spinnerMother;
    private Spinner spinnerFather;
    private ImageView imageAvatar;
    private CheckBox checkIsMe;
    private List<Long> parentIds;
    private Uri selectedPhotoUri;
    private ActivityResultLauncher<String[]> photoPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_person);

        database = AppDatabase.getInstance(this);

        imageAvatar = findViewById(R.id.imageAvatar);
        editLastName = findViewById(R.id.editLastName);
        editFirstName = findViewById(R.id.editName);
        editMiddleName = findViewById(R.id.editMiddleName);
        editBirthYear = findViewById(R.id.editBirthYear);
        editOrigin = findViewById(R.id.editOrigin);
        textAge = findViewById(R.id.textAge);
        spinnerMother = findViewById(R.id.spinnerMother);
        spinnerFather = findViewById(R.id.spinnerFather);
        checkIsMe = findViewById(R.id.checkIsMe);

        photoPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                } catch (SecurityException ignored) {
                }
                selectedPhotoUri = uri;
                imageAvatar.setImageURI(uri);
            }
        });

        Button buttonPickPhoto = findViewById(R.id.buttonPickPhoto);
        buttonPickPhoto.setOnClickListener(v -> photoPicker.launch(new String[]{"image/*"}));

        editBirthYear.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateAge();
            }
        });

        loadParents();

        Button buttonSave = findViewById(R.id.buttonSave);
        buttonSave.setOnClickListener(v -> savePerson());
    }

    private void loadParents() {
        List<Person> people = database.personDao().getAll();
        List<String> labels = new ArrayList<>();
        parentIds = new ArrayList<>();

        labels.add(getString(R.string.parent_unknown));
        parentIds.add(null);

        if (people != null) {
            for (Person person : people) {
                labels.add(PersonUtils.getDisplayName(person));
                parentIds.add(person.id);
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                labels
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMother.setAdapter(adapter);
        spinnerFather.setAdapter(adapter);
    }

    private void updateAge() {
        String yearText = editBirthYear.getText().toString().trim();
        Integer age = null;
        if (!yearText.isEmpty()) {
            try {
                int year = Integer.parseInt(yearText);
                age = PersonUtils.calculateAge(year);
            } catch (NumberFormatException ignored) {
            }
        }

        if (age == null) {
            textAge.setText(R.string.age_unknown);
        } else {
            textAge.setText(getString(R.string.age_inline, age));
        }
    }

    private void savePerson() {
        String firstName = editFirstName.getText().toString().trim();
        String lastName = editLastName.getText().toString().trim();
        String middleName = editMiddleName.getText().toString().trim();
        String origin = editOrigin.getText().toString().trim();
        String yearText = editBirthYear.getText().toString().trim();

        if (firstName.isEmpty()) {
            Toast.makeText(this, R.string.name_required, Toast.LENGTH_SHORT).show();
            return;
        }

        Integer birthYear = null;
        if (!yearText.isEmpty()) {
            try {
                birthYear = Integer.parseInt(yearText);
            } catch (NumberFormatException ignored) {
            }
        }

        Long motherId = parentIds.get(spinnerMother.getSelectedItemPosition());
        Long fatherId = parentIds.get(spinnerFather.getSelectedItemPosition());

        if (motherId != null && fatherId != null && motherId.equals(fatherId)) {
            Toast.makeText(this, R.string.same_parent_error, Toast.LENGTH_SHORT).show();
            return;
        }

        Person person = new Person();
        person.firstName = firstName;
        person.lastName = lastName.isEmpty() ? null : lastName;
        person.middleName = middleName.isEmpty() ? null : middleName;
        person.origin = origin.isEmpty() ? null : origin;
        person.birthYear = birthYear;
        person.photoUri = selectedPhotoUri != null ? selectedPhotoUri.toString() : null;
        person.motherId = motherId;
        person.fatherId = fatherId;
        person.isRoot = checkIsMe.isChecked();

        if (person.isRoot) {
            database.personDao().clearRoot();
        } else if (database.personDao().getRoot() == null) {
            person.isRoot = true;
        }

        database.personDao().insert(person);
        finish();
    }
}
