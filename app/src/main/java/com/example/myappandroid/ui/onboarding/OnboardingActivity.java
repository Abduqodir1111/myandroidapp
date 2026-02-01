package com.example.myappandroid.ui.onboarding;

import android.content.Intent;
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
import com.example.myappandroid.ui.tree.TreeActivity;
import com.example.myappandroid.util.DatePickerDialogHelper;
import com.example.myappandroid.util.DateUtils;
import com.example.myappandroid.util.ImageStorage;
import com.example.myappandroid.util.InitialsUtils;
import com.example.myappandroid.util.Prefs;
import com.example.myappandroid.util.ValidationUtils;
import com.example.myappandroid.viewmodel.OnboardingViewModel;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.time.LocalDate;
import java.io.IOException;

public class OnboardingActivity extends AppCompatActivity {
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

    private OnboardingViewModel viewModel;
    private ActivityResultLauncher<String> photoPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Prefs.isOnboardingDone(this)) {
            startActivity(new Intent(this, TreeActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_onboarding);

        viewModel = new ViewModelProvider(this).get(OnboardingViewModel.class);

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
                updateInitials();
            }
        };

        editFirstName.addTextChangedListener(initialsWatcher);
        editLastName.addTextChangedListener(initialsWatcher);
        editMiddleName.addTextChangedListener(initialsWatcher);

        Button buttonContinue = findViewById(R.id.buttonContinue);
        buttonContinue.setOnClickListener(v -> onContinue());

        viewModel.getSaveResult().observe(this, success -> {
            if (Boolean.TRUE.equals(success)) {
                Prefs.setOnboardingDone(this, true);
                startActivity(new Intent(this, TreeActivity.class));
                finish();
            }
        });
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
        if (selectedPhotoUri != null) {
            return;
        }
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

    private String safeText(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString();
    }

    private void onContinue() {
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

        if (!valid) {
            return;
        }

        Person person = new Person();
        person.firstName = firstName;
        person.lastName = lastName;
        person.middleName = middleName;
        person.birthDate = DateUtils.toEpochMillis(selectedDate);
        person.photoUri = selectedPhotoUri != null ? selectedPhotoUri.toString() : null;

        viewModel.saveRootPerson(person);
    }

    private void clearErrors() {
        layoutFirstName.setError(null);
        layoutLastName.setError(null);
        layoutMiddleName.setError(null);
        layoutBirthDate.setError(null);
    }
}
