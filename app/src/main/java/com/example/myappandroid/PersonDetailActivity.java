package com.example.myappandroid;

import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.myappandroid.data.AppDatabase;
import com.example.myappandroid.data.Person;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

public class PersonDetailActivity extends AppCompatActivity {
    public static final String EXTRA_PERSON_ID = "person_id";

    private AppDatabase database;
    private Map<Long, Person> personMap;
    private Map<Long, List<Person>> childrenMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_person_detail);

        database = AppDatabase.getInstance(this);

        long personId = getIntent().getLongExtra(EXTRA_PERSON_ID, -1);
        if (personId == -1) {
            finish();
            return;
        }

        Person person = database.personDao().getById(personId);
        if (person == null) {
            finish();
            return;
        }

        List<Person> allPeople = database.personDao().getAll();
        buildMaps(allPeople);

        bindPerson(person);
    }

    private void buildMaps(List<Person> allPeople) {
        personMap = new HashMap<>();
        childrenMap = new HashMap<>();

        if (allPeople == null) {
            return;
        }

        for (Person person : allPeople) {
            personMap.put(person.id, person);
        }

        for (Person person : allPeople) {
            if (person.motherId != null) {
                addChild(person.motherId, person);
            }
            if (person.fatherId != null) {
                addChild(person.fatherId, person);
            }
        }
    }

    private void addChild(Long parentId, Person child) {
        List<Person> children = childrenMap.get(parentId);
        if (children == null) {
            children = new ArrayList<>();
            childrenMap.put(parentId, children);
        }
        children.add(child);
    }

    private void bindPerson(Person person) {
        ImageView imageAvatar = findViewById(R.id.imageAvatar);
        TextView textName = findViewById(R.id.textPersonName);
        TextView textBirth = findViewById(R.id.textBirth);
        TextView textOrigin = findViewById(R.id.textOrigin);
        TextView textMother = findViewById(R.id.textMother);
        TextView textFather = findViewById(R.id.textFather);
        TextView textAncestors = findViewById(R.id.textAncestors);
        TextView textDescendants = findViewById(R.id.textDescendants);

        if (person.photoUri != null && !person.photoUri.trim().isEmpty()) {
            imageAvatar.setImageURI(Uri.parse(person.photoUri));
        } else {
            imageAvatar.setImageResource(R.drawable.ic_person_placeholder);
        }

        textName.setText(PersonUtils.getDisplayName(person));
        textBirth.setText(PersonUtils.buildBirthLine(this, person));

        if (person.origin != null && !person.origin.trim().isEmpty()) {
            textOrigin.setText(getString(R.string.origin_line, person.origin));
        } else {
            textOrigin.setText(getString(R.string.origin_line, getString(R.string.no_data)));
        }

        String motherName = getParentName(person.motherId);
        String fatherName = getParentName(person.fatherId);

        textMother.setText(getString(R.string.mother_line, motherName));
        textFather.setText(getString(R.string.father_line, fatherName));

        String ancestors = buildAncestors(person);
        if (ancestors.isEmpty()) {
            ancestors = getString(R.string.no_data);
        }
        textAncestors.setText(ancestors);

        String descendants = buildDescendants(person);
        if (descendants.isEmpty()) {
            descendants = getString(R.string.no_data);
        }
        textDescendants.setText(descendants);
    }

    private String getParentName(Long parentId) {
        if (parentId == null) {
            return getString(R.string.no_data);
        }
        Person parent = personMap.get(parentId);
        if (parent == null) {
            return getString(R.string.no_data);
        }
        return PersonUtils.getDisplayName(parent);
    }

    private String buildAncestors(Person person) {
        StringBuilder builder = new StringBuilder();
        Set<Long> visited = new HashSet<>();

        appendAncestor(builder, person.motherId, getString(R.string.mother_label), 0, visited);
        appendAncestor(builder, person.fatherId, getString(R.string.father_label), 0, visited);

        return builder.toString().trim();
    }

    private void appendAncestor(StringBuilder builder, Long parentId, String label, int depth, Set<Long> visited) {
        if (parentId == null) {
            return;
        }
        Person parent = personMap.get(parentId);
        if (parent == null) {
            return;
        }

        appendIndent(builder, depth);
        builder.append(label).append(": ").append(PersonUtils.getDisplayName(parent));
        if (parent.origin != null && !parent.origin.trim().isEmpty()) {
            builder.append(" - ").append(parent.origin.trim());
        }
        builder.append("\n");

        if (!visited.add(parent.id)) {
            return;
        }

        appendAncestor(builder, parent.motherId, getString(R.string.mother_label), depth + 1, visited);
        appendAncestor(builder, parent.fatherId, getString(R.string.father_label), depth + 1, visited);
    }

    private String buildDescendants(Person person) {
        StringBuilder builder = new StringBuilder();
        Set<Long> visited = new HashSet<>();
        appendDescendants(builder, person.id, 0, visited);
        return builder.toString().trim();
    }

    private void appendDescendants(StringBuilder builder, long personId, int depth, Set<Long> visited) {
        List<Person> children = childrenMap.get(personId);
        if (children == null || children.isEmpty()) {
            return;
        }
        for (Person child : children) {
            appendIndent(builder, depth);
            builder.append(getString(R.string.child_label)).append(": ").append(PersonUtils.getDisplayName(child));
            if (child.origin != null && !child.origin.trim().isEmpty()) {
                builder.append(" - ").append(child.origin.trim());
            }
            builder.append("\n");

            if (!visited.add(child.id)) {
                continue;
            }
            appendDescendants(builder, child.id, depth + 1, visited);
        }
    }

    private void appendIndent(StringBuilder builder, int depth) {
        for (int i = 0; i < depth; i++) {
            builder.append("  ");
        }
    }
}
