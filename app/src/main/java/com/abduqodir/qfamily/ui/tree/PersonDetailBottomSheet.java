package com.abduqodir.qfamily.ui.tree;

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
import com.abduqodir.qfamily.R;
import com.abduqodir.qfamily.data.Person;
import com.abduqodir.qfamily.repository.PersonRepository;
import com.abduqodir.qfamily.repository.RepositoryCallback;
import com.abduqodir.qfamily.util.DateUtils;
import com.abduqodir.qfamily.util.InitialsUtils;
import com.abduqodir.qfamily.util.PersonFormatter;
import com.abduqodir.qfamily.util.Prefs;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.time.LocalDate;

public class PersonDetailBottomSheet extends BottomSheetDialogFragment {
    private static final String ARG_PERSON_ID = "person_id";
    private PersonRepository repository;

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
        repository = new PersonRepository(requireContext());
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
        TextView textSpouse = view.findViewById(R.id.textSpouse);
        TextView textChildren = view.findViewById(R.id.textChildren);
        Button buttonEdit = view.findViewById(R.id.buttonEdit);
        Button buttonDelete = view.findViewById(R.id.buttonDelete);

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

        textSpouse.setVisibility(View.GONE);
        textChildren.setVisibility(View.GONE);
        bindRelations(person, textSpouse, textChildren);

        buttonEdit.setOnClickListener(v -> {
            if (getContext() != null) {
                startActivity(new android.content.Intent(getContext(), EditPersonActivity.class)
                        .putExtra(EditPersonActivity.EXTRA_PERSON_ID, person.id));
                dismiss();
            }
        });

        buttonDelete.setOnClickListener(v -> showDeleteConfirm(person));
    }

    private void bindRelations(Person person, TextView textSpouse, TextView textChildren) {
        if (person == null || repository == null) {
            return;
        }
        if (person.spouseId != null) {
            repository.getPersonById(person.spouseId, new RepositoryCallback<Person>() {
                @Override
                public void onComplete(Person spouse) {
                    if (!isAdded() || spouse == null) {
                        return;
                    }
                    String name = PersonFormatter.getFullName(spouse);
                    if (!name.isEmpty()) {
                        textSpouse.setText(getString(R.string.spouse_line, name));
                        textSpouse.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                }
            });
        }

        repository.getChildren(person.id, new RepositoryCallback<java.util.List<Person>>() {
            @Override
            public void onComplete(java.util.List<Person> children) {
                if (!isAdded() || children == null || children.isEmpty()) {
                    return;
                }
                StringBuilder builder = new StringBuilder();
                for (Person child : children) {
                    String name = PersonFormatter.getFullName(child);
                    if (name.isEmpty()) {
                        continue;
                    }
                    if (builder.length() > 0) {
                        builder.append(", ");
                    }
                    builder.append(name);
                }
                if (builder.length() > 0) {
                    textChildren.setText(getString(R.string.children_line, builder.toString()));
                    textChildren.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(Throwable throwable) {
            }
        });
    }

    private void showDeleteConfirm(Person person) {
        if (person == null || getContext() == null) {
            return;
        }
        String title = person.isRoot
                ? getString(R.string.delete_profile_root_confirm)
                : getString(R.string.delete_profile_confirm, PersonFormatter.getFullName(person));
        new MaterialAlertDialogBuilder(getContext(), R.style.ThemeOverlay_MyAPPAndroid_DialogAnimation)
                .setMessage(title)
                .setPositiveButton(R.string.delete_action, (dialog, which) -> deletePerson(person))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deletePerson(Person person) {
        if (person == null || repository == null) {
            return;
        }
        repository.deletePerson(person.id, new RepositoryCallback<Boolean>() {
            @Override
            public void onComplete(Boolean result) {
                if (!isAdded()) {
                    return;
                }
                if (person.isRoot) {
                    Prefs.setOnboardingDone(requireContext(), false);
                    android.content.Intent intent = new android.content.Intent(requireContext(), com.abduqodir.qfamily.ui.onboarding.OnboardingActivity.class);
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                } else if (getActivity() instanceof TreeActivity) {
                    ((TreeActivity) getActivity()).refreshTree();
                }
                dismiss();
            }

            @Override
            public void onError(Throwable throwable) {
            }
        });
    }
}

