package com.example.myappandroid.ui.tree;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.myappandroid.R;
import com.example.myappandroid.data.Person;
import com.example.myappandroid.util.DatePickerDialogHelper;
import com.example.myappandroid.util.DateUtils;
import com.example.myappandroid.util.ImageStorage;
import com.example.myappandroid.util.InitialsUtils;
import com.example.myappandroid.util.ValidationUtils;
import com.example.myappandroid.viewmodel.EditPersonViewModel;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.io.IOException;
import java.time.LocalDate;

public class EditPersonActivity extends AppCompatActivity {
    public static final String EXTRA_PERSON_ID = "person_id";

    private TextInputLayout layoutFirstName;
    private TextInputLayout layoutLastName;
    private TextInputLayout layoutMiddleName;
    private TextInputLayout layoutBirthDate;
    private TextInputEditText editFirstName;
    private TextInputEditText editLastName;
    private TextInputEditText editMiddleName;
    private TextInputEditText editBirthDate;
    private TextView textAge;
    private ImageView imageAvatar;
    private TextView textInitials;

    private LocalDate selectedDate;
    private Uri selectedPhotoUri;
    private Person currentPerson;

    private EditPersonViewModel viewModel;
    private ActivityResultLauncher<String> photoPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_person);

        long personId = getIntent().getLongExtra(EXTRA_PERSON_ID, -1L);
        if (personId == -1L) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(EditPersonViewModel.class);

        layoutFirstName = findViewById(R.id.layoutFirstName);
        layoutLastName = findViewById(R.id.layoutLastName);
        layoutMiddleName = findViewById(R.id.layoutMiddleName);
        layoutBirthDate = findViewById(R.id.layoutBirthDate);
        editFirstName = findViewById(R.id.editFirstName);
        editLastName = findViewById(R.id.editLastName);
        editMiddleName = findViewById(R.id.editMiddleName);
        editBirthDate = findViewById(R.id.editBirthDate);
        textAge = findViewById(R.id.textAge);
        imageAvatar = findViewById(R.id.imageAvatar);
        textInitials = findViewById(R.id.textInitials);

        photoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                try {
                    String saved = ImageStorage.persistImage(this, uri);
                    if (saved != null) {
                        selectedPhotoUri = Uri.parse(saved);
                    }
                    Uri preview = selectedPhotoUri != null ? selectedPhotoUri : uri;
                    imageAvatar.setImageURI(preview);
                    imageAvatar.setVisibility(View.VISIBLE);
                    textInitials.setVisibility(View.GONE);
                } catch (IOException e) {
                    selectedPhotoUri = null;
                    imageAvatar.setImageURI(uri);
                    imageAvatar.setVisibility(View.VISIBLE);
                    textInitials.setVisibility(View.GONE);
                }
            }
        });

        Button buttonPickPhoto = findViewById(R.id.buttonPickPhoto);
        buttonPickPhoto.setOnClickListener(v -> photoPicker.launch("image/*"));

        editBirthDate.setOnClickListener(v -> showDatePicker());
        layoutBirthDate.setEndIconOnClickListener(v -> showDatePicker());

        TextWatcher initialsWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (selectedPhotoUri == null) {
                    updateInitials();
                }
            }
        };
        editFirstName.addTextChangedListener(initialsWatcher);
        editLastName.addTextChangedListener(initialsWatcher);
        editMiddleName.addTextChangedListener(initialsWatcher);

        Button buttonSave = findViewById(R.id.buttonSave);
        buttonSave.setOnClickListener(v -> onSave());

        viewModel.getPerson().observe(this, this::bindPerson);
        viewModel.getSaveResult().observe(this, success -> {
            if (Boolean.TRUE.equals(success)) {
                setResult(RESULT_OK);
                finish();
            }
        });

        viewModel.loadPerson(personId);
    }

    private void bindPerson(Person person) {
        if (person == null) {
            finish();
            return;
        }
        currentPerson = person;
        editFirstName.setText(safe(person.firstName));
        editLastName.setText(safe(person.lastName));
        editMiddleName.setText(safe(person.middleName));

        selectedDate = DateUtils.fromEpochMillis(person.birthDate);
        if (selectedDate != null) {
            editBirthDate.setText(DateUtils.formatDate(selectedDate));
        }
        updateAge();

        if (person.photoUri != null && !person.photoUri.trim().isEmpty()) {
            try {
                selectedPhotoUri = Uri.parse(person.photoUri);
                imageAvatar.setImageURI(selectedPhotoUri);
                imageAvatar.setVisibility(View.VISIBLE);
                textInitials.setVisibility(View.GONE);
            } catch (RuntimeException e) {
                selectedPhotoUri = null;
                updateInitials();
            }
        } else {
            updateInitials();
        }
    }

    private void showDatePicker() {
        DatePickerDialogHelper.show(this, selectedDate, date -> {
            selectedDate = date;
            editBirthDate.setText(DateUtils.formatDate(selectedDate));
            updateAge();
        });
    }

    private void updateAge() {
        Integer age = DateUtils.calculateAge(selectedDate);
        if (age == null) {
            textAge.setText(R.string.age_unknown);
        } else {
            textAge.setText(getString(R.string.age_inline, age));
        }
    }

    private void updateInitials() {
        String initials = InitialsUtils.buildInitials(
                safeText(editLastName),
                safeText(editFirstName),
                safeText(editMiddleName)
        );
        if (initials.isEmpty()) {
            textInitials.setText(R.string.initials_placeholder);
        } else {
            textInitials.setText(initials);
        }
        textInitials.setVisibility(View.VISIBLE);
        imageAvatar.setVisibility(View.INVISIBLE);
    }

    private void onSave() {
        clearErrors();

        String firstName = safeText(editFirstName).trim();
        String lastName = safeText(editLastName).trim();
        String middleName = safeText(editMiddleName).trim();

        boolean valid = true;
        if (ValidationUtils.isBlank(firstName)) {
            layoutFirstName.setError(getString(R.string.error_required));
            valid = false;
        }
        if (ValidationUtils.isBlank(lastName)) {
            layoutLastName.setError(getString(R.string.error_required));
            valid = false;
        }
        if (ValidationUtils.isBlank(middleName)) {
            layoutMiddleName.setError(getString(R.string.error_required));
            valid = false;
        }
        if (!ValidationUtils.isValidBirthDate(selectedDate)) {
            layoutBirthDate.setError(getString(R.string.error_birth_date));
            valid = false;
        }

        if (!valid || currentPerson == null) {
            return;
        }

        currentPerson.firstName = firstName;
        currentPerson.lastName = lastName;
        currentPerson.middleName = middleName;
        currentPerson.birthDate = DateUtils.toEpochMillis(selectedDate);
        currentPerson.photoUri = selectedPhotoUri != null ? selectedPhotoUri.toString() : null;

        viewModel.updatePerson(currentPerson);
    }

    private void clearErrors() {
        layoutFirstName.setError(null);
        layoutLastName.setError(null);
        layoutMiddleName.setError(null);
        layoutBirthDate.setError(null);
    }

    private String safeText(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
