package com.abduqodir.qfamily.ui.tree;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.abduqodir.qfamily.R;
import com.abduqodir.qfamily.data.Person;
import com.abduqodir.qfamily.repository.PersonRepository;
import com.abduqodir.qfamily.repository.RepositoryCallback;
import com.abduqodir.qfamily.util.PersonFormatter;
import com.abduqodir.qfamily.viewmodel.TreeViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class TreeActivity extends AppCompatActivity {
    private static final String TAG_PERSON_DETAIL = "person_detail";
    private static final long DETAIL_OPEN_DEBOUNCE_MS = 500L;
    private TreeViewModel viewModel;
    private FamilyTreeView treeView;
    private ActivityResultLauncher<Intent> addRelativeLauncher;
    private long lastDetailOpenAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tree);

        viewModel = new ViewModelProvider(this).get(TreeViewModel.class);
        treeView = findViewById(R.id.familyTreeView);

        treeView.setOnNodeInteractionListener(new FamilyTreeView.OnNodeInteractionListener() {
            @Override
            public void onNodeSelected(Person person) {
                viewModel.setSelectedPerson(person);
            }

            @Override
            public void onNodeDoubleTap(Person person) {
                if (person == null) {
                    return;
                }
                long now = SystemClock.elapsedRealtime();
                if (now - lastDetailOpenAt < DETAIL_OPEN_DEBOUNCE_MS) {
                    return;
                }
                lastDetailOpenAt = now;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (getSupportFragmentManager().isStateSaved()) {
                    return;
                }
                if (getSupportFragmentManager().findFragmentByTag(TAG_PERSON_DETAIL) != null) {
                    return;
                }
                PersonDetailBottomSheet sheet = PersonDetailBottomSheet.newInstance(person.id);
                sheet.show(getSupportFragmentManager(), TAG_PERSON_DETAIL);
            }

            @Override
            public void onAddRelativeRequested(Person person) {
                if (person == null) {
                    return;
                }
                showAddMenu(person);
            }
        });

        viewModel.getTreeData().observe(this, treeView::setTreeData);
        viewModel.getSelectedPerson().observe(this, person -> {
            if (person == null) {
                treeView.setSelectedPersonId(-1L);
            } else {
                treeView.setSelectedPersonId(person.id);
            }
        });

        if (savedInstanceState == null) {
            viewModel.loadTree();
        }

        addRelativeLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        viewModel.loadTree();
                    }
                }
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.loadTree();
        treeView.setViewportState(
                viewModel.getTranslateX(),
                viewModel.getTranslateY(),
                viewModel.getScaleFactor()
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        viewModel.setViewportState(
                treeView.getTranslateXValue(),
                treeView.getTranslateYValue(),
                treeView.getScaleFactorValue()
        );
    }

    private void showAddMenu(Person selected) {
        if (selected == null) {
            return;
        }
        if (selected.spouseId != null) {
            new PersonRepository(this).getPersonById(selected.spouseId, new RepositoryCallback<Person>() {
                @Override
                public void onComplete(Person spouse) {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    showAddMenuInternal(selected, spouse);
                }

                @Override
                public void onError(Throwable throwable) {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    showAddMenuInternal(selected, null);
                }
            });
        } else {
            showAddMenuInternal(selected, null);
        }
    }

    private void showAddMenuInternal(Person selected, Person spouse) {
        boolean spouseExists = spouse != null;
        String spouseLabel = spouseExists
                ? buildSpouseMenuLabel(spouse)
                : getString(R.string.add_spouse);

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_MyAPPAndroid_DialogAnimation)
                .setTitle(getString(R.string.add_relative_title, PersonFormatter.getFullName(selected)))
                .setItems(new CharSequence[]{
                        getString(R.string.add_parent),
                        getString(R.string.add_child),
                        spouseLabel
                }, (dialog, which) -> {
                    if (which == 0) {
                        launchAddRelative(AddRelativeActivity.RELATION_PARENT, selected.id);
                    } else if (which == 1) {
                        launchAddRelative(AddRelativeActivity.RELATION_CHILD, selected.id);
                    } else {
                        if (spouseExists) {
                            Toast.makeText(this, R.string.spouse_already_exists, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        launchAddRelative(AddRelativeActivity.RELATION_SPOUSE, selected.id);
                    }
                })
                .show();
    }

    private String buildSpouseMenuLabel(Person spouse) {
        String name = PersonFormatter.getFullName(spouse);
        if (name.isEmpty()) {
            name = getString(R.string.no_data);
        }
        if ("female".equalsIgnoreCase(spouse.gender)) {
            return getString(R.string.wife_line, name);
        }
        if ("male".equalsIgnoreCase(spouse.gender)) {
            return getString(R.string.husband_line, name);
        }
        return getString(R.string.spouse_line, name);
    }

    private void launchAddRelative(int relationType, long targetId) {
        Intent intent = new Intent(this, AddRelativeActivity.class);
        intent.putExtra(AddRelativeActivity.EXTRA_RELATION, relationType);
        intent.putExtra(AddRelativeActivity.EXTRA_TARGET_ID, targetId);
        addRelativeLauncher.launch(intent);
    }

    public void refreshTree() {
        viewModel.loadTree();
    }
}

