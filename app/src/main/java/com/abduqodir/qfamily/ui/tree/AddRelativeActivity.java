package com.abduqodir.qfamily.ui.tree;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.abduqodir.qfamily.R;
import com.abduqodir.qfamily.data.Person;
import com.abduqodir.qfamily.util.DatePickerDialogHelper;
import com.abduqodir.qfamily.util.DateUtils;
import com.abduqodir.qfamily.util.ImageStorage;
import com.abduqodir.qfamily.util.InitialsUtils;
import com.abduqodir.qfamily.util.ValidationUtils;
import com.abduqodir.qfamily.viewmodel.AddRelativeViewModel;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.io.IOException;
import java.time.LocalDate;

public class AddRelativeActivity extends AppCompatActivity {
    public static final String EXTRA_RELATION = "relation_type";
    public static final String EXTRA_TARGET_ID = "target_id";

    public static final int RELATION_PARENT = 1;
    public static final int RELATION_CHILD = 2;
    public static final int RELATION_SPOUSE = 3;

    private TextView textTitle;
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
    private RadioButton radioMother;
    private RadioButton radioFather;
    private TextView textRelationHint;

    private LocalDate selectedDate;
    private Uri selectedPhotoUri;
    private int relationType;
    private long targetId;

    private AddRelativeViewModel viewModel;
    private ActivityResultLauncher<String> photoPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_relative);

        relationType = getIntent().getIntExtra(EXTRA_RELATION, RELATION_PARENT);
        targetId = getIntent().getLongExtra(EXTRA_TARGET_ID, -1);
        if (targetId == -1L) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(AddRelativeViewModel.class);

        textTitle = findViewById(R.id.textTitle);
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
        radioMother = findViewById(R.id.radioMother);
        radioFather = findViewById(R.id.radioFather);
        textRelationHint = findViewById(R.id.textRelationHint);

        setupTitles();

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

        Button buttonSave = findViewById(R.id.buttonSave);
        buttonSave.setOnClickListener(v -> onSave());

        viewModel.getSaveResult().observe(this, success -> {
            if (Boolean.TRUE.equals(success)) {
                setResult(RESULT_OK);
                finish();
            }
        });

        updateInitials();
    }

    private void setupTitles() {
        if (relationType == RELATION_PARENT) {
            textTitle.setText(R.string.add_parent_title);
            textRelationHint.setText(R.string.parent_role_hint);
            radioMother.setText(R.string.mother_label);
            radioFather.setText(R.string.father_label);
        } else if (relationType == RELATION_CHILD) {
            textTitle.setText(R.string.add_child_title);
            textRelationHint.setText(R.string.child_role_hint);
            radioMother.setText(R.string.mother_label);
            radioFather.setText(R.string.father_label);
        } else if (relationType == RELATION_SPOUSE) {
            textTitle.setText(R.string.add_spouse_title);
            textRelationHint.setText(R.string.spouse_role_hint);
            radioMother.setText(R.string.wife_label);
            radioFather.setText(R.string.husband_label);
        } else {
            textTitle.setText(R.string.add_relative);
            textRelationHint.setText(R.string.parent_role_hint);
            radioMother.setText(R.string.mother_label);
            radioFather.setText(R.string.father_label);
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
        if (selectedDate != null && !ValidationUtils.isValidBirthDate(selectedDate)) {
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

        boolean firstOption = radioMother.isChecked();

        if (relationType == RELATION_PARENT) {
            person.gender = firstOption ? "female" : "male";
            viewModel.addParent(targetId, person, firstOption);
        } else if (relationType == RELATION_SPOUSE) {
            person.gender = firstOption ? "female" : "male";
            viewModel.addSpouse(targetId, person);
        } else {
            viewModel.addChild(targetId, person, firstOption);
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

    private void clearErrors() {
        layoutFirstName.setError(null);
        layoutLastName.setError(null);
        layoutMiddleName.setError(null);
        layoutBirthDate.setError(null);
    }

    private String safeText(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString();
    }
}

