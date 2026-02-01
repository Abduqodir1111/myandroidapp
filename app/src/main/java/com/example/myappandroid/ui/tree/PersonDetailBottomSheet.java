package com.example.myappandroid.ui.tree;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.myappandroid.R;
import com.example.myappandroid.data.Person;
import com.example.myappandroid.repository.PersonRepository;
import com.example.myappandroid.repository.RepositoryCallback;
import com.example.myappandroid.util.DateUtils;
import com.example.myappandroid.util.InitialsUtils;
import com.example.myappandroid.util.PersonFormatter;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.time.LocalDate;

public class PersonDetailBottomSheet extends BottomSheetDialogFragment {
    private static final String ARG_PERSON_ID = "person_id";

    public static PersonDetailBottomSheet newInstance(long personId) {
        PersonDetailBottomSheet sheet = new PersonDetailBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_PERSON_ID, personId);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottomsheet_person_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        long personId = getArguments() != null ? getArguments().getLong(ARG_PERSON_ID, -1L) : -1L;
        if (personId == -1L) {
            dismiss();
            return;
        }
        PersonRepository repository = new PersonRepository(requireContext());
        repository.getPersonById(personId, new RepositoryCallback<Person>() {
            @Override
            public void onComplete(Person person) {
                bind(view, person);
            }

            @Override
            public void onError(Throwable throwable) {
            }
        });
    }

    private void bind(View view, Person person) {
        if (person == null) {
            dismiss();
            return;
        }

        ImageView imageAvatar = view.findViewById(R.id.imageAvatar);
        TextView textInitials = view.findViewById(R.id.textInitials);
        TextView textName = view.findViewById(R.id.textName);
        TextView textBirth = view.findViewById(R.id.textBirth);
        TextView textAge = view.findViewById(R.id.textAge);
        Button buttonEdit = view.findViewById(R.id.buttonEdit);

        boolean loaded = false;
        if (person.photoUri != null && !person.photoUri.trim().isEmpty()) {
            try {
                imageAvatar.setImageURI(Uri.parse(person.photoUri));
                imageAvatar.setVisibility(View.VISIBLE);
                textInitials.setVisibility(View.GONE);
                loaded = true;
            } catch (RuntimeException e) {
                loaded = false;
            }
        }
        if (!loaded) {
            String initials = InitialsUtils.buildInitials(person.lastName, person.firstName, person.middleName);
            if (initials.isEmpty()) {
                initials = getString(R.string.initials_placeholder);
            }
            textInitials.setText(initials);
            textInitials.setVisibility(View.VISIBLE);
            imageAvatar.setVisibility(View.INVISIBLE);
            if (person.photoUri != null) {
                person.photoUri = null;
                new PersonRepository(requireContext()).updatePerson(person);
            }
        }

        textName.setText(PersonFormatter.getFullName(person));

        LocalDate date = DateUtils.fromEpochMillis(person.birthDate);
        if (date != null) {
            textBirth.setText(getString(R.string.birth_date_line, DateUtils.formatDate(date)));
            Integer age = DateUtils.calculateAge(date);
            if (age == null) {
                textAge.setText(R.string.age_unknown);
            } else {
                textAge.setText(getString(R.string.age_inline, age));
            }
        } else {
            textBirth.setText(getString(R.string.birth_date_line, getString(R.string.no_data)));
            textAge.setText(R.string.age_unknown);
        }

        buttonEdit.setOnClickListener(v -> {
            if (getContext() != null) {
                startActivity(new android.content.Intent(getContext(), EditPersonActivity.class)
                        .putExtra(EditPersonActivity.EXTRA_PERSON_ID, person.id));
                dismiss();
            }
        });
    }
}
