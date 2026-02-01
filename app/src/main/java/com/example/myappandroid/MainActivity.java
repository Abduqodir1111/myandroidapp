package com.example.myappandroid;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myappandroid.data.AppDatabase;
import com.example.myappandroid.data.Person;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private AppDatabase database;
    private TreeAdapter adapter;
    private TextView textEmpty;
    private TextView textTitle;
    private ImageView imageCenterAvatar;
    private Map<Long, Person> personMap = new HashMap<>();
    private Map<Long, List<Person>> childrenMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        database = AppDatabase.getInstance(this);

        RecyclerView recyclerView = findViewById(R.id.recyclerPeople);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TreeAdapter(new TreeAdapter.OnPersonClickListener() {
            @Override
            public void onPersonSingleClick(Person person) {
                setRoot(person);
                loadTree();
            }

            @Override
            public void onPersonDoubleClick(Person person) {
                showPersonSheet(person);
            }
        });
        recyclerView.setAdapter(adapter);

        textEmpty = findViewById(R.id.textEmpty);
        textTitle = findViewById(R.id.textTitle);
        imageCenterAvatar = findViewById(R.id.imageCenterAvatar);

        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(v -> {
            startActivity(new android.content.Intent(this, AddPersonActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTree();
    }

    private void loadTree() {
        List<Person> people = database.personDao().getAll();
        if (people == null || people.isEmpty()) {
            adapter.setItems(new ArrayList<>());
            textEmpty.setText(R.string.empty_list);
            textEmpty.setVisibility(View.VISIBLE);
            textTitle.setText(R.string.title_main);
            imageCenterAvatar.setImageResource(R.drawable.ic_person_placeholder);
            personMap.clear();
            childrenMap.clear();
            return;
        }

        buildMaps(people);

        Person root = database.personDao().getRoot();
        if (root == null) {
            root = people.get(0);
            database.personDao().clearRoot();
            root.isRoot = true;
            database.personDao().update(root);
        }

        String rootName = PersonUtils.getDisplayName(root);
        if (rootName == null || rootName.trim().isEmpty()) {
            textTitle.setText(R.string.title_main);
        } else {
            textTitle.setText(getString(R.string.title_main_root, rootName));
        }

        updateCenterAvatar(root);

        List<TreeAdapter.TreeNode> nodes = new ArrayList<>();
        Set<Long> visited = new HashSet<>();

        appendAncestor(nodes, root.motherId, getString(R.string.mother_label), 0, visited);
        appendAncestor(nodes, root.fatherId, getString(R.string.father_label), 0, visited);

        adapter.setItems(nodes);
        if (nodes.isEmpty()) {
            textEmpty.setText(R.string.no_ancestors);
            textEmpty.setVisibility(View.VISIBLE);
        } else {
            textEmpty.setVisibility(View.GONE);
        }
    }

    private void setRoot(Person person) {
        if (person == null) {
            return;
        }
        if (person.isRoot) {
            return;
        }
        database.personDao().clearRoot();
        person.isRoot = true;
        database.personDao().update(person);
    }

    private void updateCenterAvatar(Person root) {
        if (root != null && root.photoUri != null && !root.photoUri.trim().isEmpty()) {
            imageCenterAvatar.setImageURI(Uri.parse(root.photoUri));
        } else {
            imageCenterAvatar.setImageResource(R.drawable.ic_person_placeholder);
        }
    }

    private void buildMaps(List<Person> people) {
        personMap = new HashMap<>();
        childrenMap = new HashMap<>();

        if (people == null) {
            return;
        }

        for (Person person : people) {
            personMap.put(person.id, person);
        }

        for (Person person : people) {
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

    private void appendAncestor(List<TreeAdapter.TreeNode> nodes,
                                Long parentId,
                                String label,
                                int depth,
                                Set<Long> visited) {
        if (parentId == null) {
            return;
        }
        Person parent = personMap.get(parentId);
        if (parent == null) {
            return;
        }

        nodes.add(new TreeAdapter.TreeNode(parent, depth, label));
        if (!visited.add(parent.id)) {
            return;
        }

        appendAncestor(nodes, parent.motherId, getString(R.string.mother_label), depth + 1, visited);
        appendAncestor(nodes, parent.fatherId, getString(R.string.father_label), depth + 1, visited);
    }

    private void showPersonSheet(Person person) {
        if (person == null) {
            return;
        }
        if (personMap.isEmpty()) {
            List<Person> people = database.personDao().getAll();
            buildMaps(people);
        }

        View view = getLayoutInflater().inflate(R.layout.bottomsheet_person_detail, null);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(view);

        ImageView sheetAvatar = view.findViewById(R.id.sheetAvatar);
        TextView sheetName = view.findViewById(R.id.sheetName);
        TextView sheetBirth = view.findViewById(R.id.sheetBirth);
        TextView sheetOrigin = view.findViewById(R.id.sheetOrigin);
        TextView sheetMother = view.findViewById(R.id.sheetMother);
        TextView sheetFather = view.findViewById(R.id.sheetFather);
        TextView sheetAncestors = view.findViewById(R.id.sheetAncestors);
        TextView sheetDescendants = view.findViewById(R.id.sheetDescendants);

        if (person.photoUri != null && !person.photoUri.trim().isEmpty()) {
            sheetAvatar.setImageURI(Uri.parse(person.photoUri));
        } else {
            sheetAvatar.setImageResource(R.drawable.ic_person_placeholder);
        }

        sheetName.setText(PersonUtils.getDisplayName(person));
        sheetBirth.setText(PersonUtils.buildBirthLine(this, person));

        if (person.origin != null && !person.origin.trim().isEmpty()) {
            sheetOrigin.setText(getString(R.string.origin_line, person.origin));
        } else {
            sheetOrigin.setText(getString(R.string.origin_line, getString(R.string.no_data)));
        }

        String motherName = getParentName(person.motherId);
        String fatherName = getParentName(person.fatherId);

        sheetMother.setText(getString(R.string.mother_line, motherName));
        sheetFather.setText(getString(R.string.father_line, fatherName));

        String ancestors = buildAncestors(person);
        if (ancestors.isEmpty()) {
            ancestors = getString(R.string.no_data);
        }
        sheetAncestors.setText(ancestors);

        String descendants = buildDescendants(person);
        if (descendants.isEmpty()) {
            descendants = getString(R.string.no_data);
        }
        sheetDescendants.setText(descendants);

        dialog.show();
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

        appendAncestorText(builder, person.motherId, getString(R.string.mother_label), 0, visited);
        appendAncestorText(builder, person.fatherId, getString(R.string.father_label), 0, visited);

        return builder.toString().trim();
    }

    private void appendAncestorText(StringBuilder builder, Long parentId, String label, int depth, Set<Long> visited) {
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

        appendAncestorText(builder, parent.motherId, getString(R.string.mother_label), depth + 1, visited);
        appendAncestorText(builder, parent.fatherId, getString(R.string.father_label), depth + 1, visited);
    }

    private String buildDescendants(Person person) {
        StringBuilder builder = new StringBuilder();
        Set<Long> visited = new HashSet<>();
        appendDescendantsText(builder, person.id, 0, visited);
        return builder.toString().trim();
    }

    private void appendDescendantsText(StringBuilder builder, long personId, int depth, Set<Long> visited) {
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
            appendDescendantsText(builder, child.id, depth + 1, visited);
        }
    }

    private void appendIndent(StringBuilder builder, int depth) {
        for (int i = 0; i < depth; i++) {
            builder.append("  ");
        }
    }
}
