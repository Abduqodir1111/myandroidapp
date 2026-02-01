package com.example.myappandroid.viewmodel;

import android.app.Application;
import android.content.res.Resources;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.myappandroid.R;
import com.example.myappandroid.data.Person;
import com.example.myappandroid.repository.PersonRepository;
import com.example.myappandroid.repository.RepositoryCallback;
import com.example.myappandroid.ui.tree.model.TreeData;
import com.example.myappandroid.ui.tree.model.TreeEdge;
import com.example.myappandroid.ui.tree.model.TreeNode;
import com.example.myappandroid.util.InitialsUtils;
import com.example.myappandroid.util.PersonFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class TreeViewModel extends AndroidViewModel {
    private static final int MAX_UP_LEVELS = 2;
    private static final int MAX_DOWN_LEVELS = 2;

    private final PersonRepository repository;
    private final MutableLiveData<TreeData> treeData = new MutableLiveData<>();
    private final MutableLiveData<Person> rootPerson = new MutableLiveData<>();
    private final MutableLiveData<Person> selectedPerson = new MutableLiveData<>();

    private float translateX;
    private float translateY;
    private float scaleFactor = 1f;

    public TreeViewModel(@NonNull Application application) {
        super(application);
        repository = new PersonRepository(application);
    }

    public LiveData<TreeData> getTreeData() {
        return treeData;
    }

    public LiveData<Person> getRootPerson() {
        return rootPerson;
    }

    public LiveData<Person> getSelectedPerson() {
        return selectedPerson;
    }

    public void setSelectedPerson(Person person) {
        selectedPerson.setValue(person);
    }

    public void loadTree() {
        repository.getAllPeople(new RepositoryCallback<List<Person>>() {
            @Override
            public void onComplete(List<Person> people) {
                if (people == null || people.isEmpty()) {
                    rootPerson.setValue(null);
                    treeData.setValue(new TreeData(new ArrayList<>(), new ArrayList<>()));
                    return;
                }
                Person root = findRoot(people);
                rootPerson.setValue(root);
                treeData.setValue(buildTreeData(people, root));
            }

            @Override
            public void onError(Throwable throwable) {
                treeData.setValue(new TreeData(new ArrayList<>(), new ArrayList<>()));
            }
        });
    }

    public float getTranslateX() {
        return translateX;
    }

    public float getTranslateY() {
        return translateY;
    }

    public float getScaleFactor() {
        return scaleFactor;
    }

    public void setViewportState(float translateX, float translateY, float scaleFactor) {
        this.translateX = translateX;
        this.translateY = translateY;
        this.scaleFactor = scaleFactor;
    }

    private Person findRoot(List<Person> people) {
        for (Person person : people) {
            if (person.isRoot) {
                return person;
            }
        }
        return people.get(0);
    }

    private TreeData buildTreeData(List<Person> people, Person root) {
        Map<Long, Person> personMap = new HashMap<>();
        Map<Long, List<Person>> childrenMap = new HashMap<>();
        for (Person person : people) {
            personMap.put(person.id, person);
        }
        for (Person person : people) {
            if (person.motherId != null) {
                childrenMap.computeIfAbsent(person.motherId, id -> new ArrayList<>()).add(person);
            }
            if (person.fatherId != null) {
                childrenMap.computeIfAbsent(person.fatherId, id -> new ArrayList<>()).add(person);
            }
        }

        Map<Long, TreeNode> nodesById = new HashMap<>();
        Map<Integer, List<TreeNode>> levelMap = new HashMap<>();
        List<TreeEdge> edges = new ArrayList<>();

        addNode(nodesById, levelMap, root, 0);

        buildAncestors(root, personMap, nodesById, levelMap, edges);
        buildDescendants(root, childrenMap, nodesById, levelMap, edges);
        addSpouses(personMap, nodesById, levelMap, edges);

        Resources resources = getApplication().getResources();
        float spacingX = resources.getDimension(R.dimen.tree_spacing_x);
        float spacingY = resources.getDimension(R.dimen.tree_spacing_y);
        for (Map.Entry<Integer, List<TreeNode>> entry : levelMap.entrySet()) {
            List<TreeNode> nodes = entry.getValue();
            int count = nodes.size();
            for (int i = 0; i < count; i++) {
                TreeNode node = nodes.get(i);
                node.x = (i - (count - 1) / 2f) * spacingX;
                node.y = node.level * spacingY;
            }
        }

        alignChildrenBetweenParents(nodesById, personMap);

        return new TreeData(new ArrayList<>(nodesById.values()), edges);
    }

    private void buildAncestors(Person root,
                                Map<Long, Person> personMap,
                                Map<Long, TreeNode> nodesById,
                                Map<Integer, List<TreeNode>> levelMap,
                                List<TreeEdge> edges) {
        Queue<PersonLevel> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(new PersonLevel(root, 0));
        visited.add(root.id);

        while (!queue.isEmpty()) {
            PersonLevel current = queue.poll();
            if (current.level <= -MAX_UP_LEVELS) {
                continue;
            }
            Person person = current.person;
            if (person.motherId != null) {
                Person mother = personMap.get(person.motherId);
                if (mother != null) {
                    addNode(nodesById, levelMap, mother, current.level - 1);
                    edges.add(new TreeEdge(mother.id, person.id));
                    if (visited.add(mother.id)) {
                        queue.add(new PersonLevel(mother, current.level - 1));
                    }
                }
            }
            if (person.fatherId != null) {
                Person father = personMap.get(person.fatherId);
                if (father != null) {
                    addNode(nodesById, levelMap, father, current.level - 1);
                    edges.add(new TreeEdge(father.id, person.id));
                    if (visited.add(father.id)) {
                        queue.add(new PersonLevel(father, current.level - 1));
                    }
                }
            }
        }
    }

    private void buildDescendants(Person root,
                                  Map<Long, List<Person>> childrenMap,
                                  Map<Long, TreeNode> nodesById,
                                  Map<Integer, List<TreeNode>> levelMap,
                                  List<TreeEdge> edges) {
        Queue<PersonLevel> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(new PersonLevel(root, 0));
        visited.add(root.id);

        while (!queue.isEmpty()) {
            PersonLevel current = queue.poll();
            if (current.level >= MAX_DOWN_LEVELS) {
                continue;
            }
            List<Person> children = childrenMap.get(current.person.id);
            if (children == null) {
                continue;
            }
            for (Person child : children) {
                addNode(nodesById, levelMap, child, current.level + 1);
                edges.add(new TreeEdge(current.person.id, child.id));
                if (visited.add(child.id)) {
                    queue.add(new PersonLevel(child, current.level + 1));
                }
            }
        }
    }

    private void addSpouses(Map<Long, Person> personMap,
                            Map<Long, TreeNode> nodesById,
                            Map<Integer, List<TreeNode>> levelMap,
                            List<TreeEdge> edges) {
        Set<String> spouseEdges = new HashSet<>();
        List<TreeNode> snapshot = new ArrayList<>(nodesById.values());

        for (TreeNode node : snapshot) {
            Person person = node.person;
            if (person == null || person.spouseId == null) {
                continue;
            }
            Person spousePerson = personMap.get(person.spouseId);
            if (spousePerson == null) {
                continue;
            }

            TreeNode spouseNode = nodesById.get(spousePerson.id);
            if (spouseNode == null) {
                String initials = InitialsUtils.buildInitials(
                        spousePerson.lastName,
                        spousePerson.firstName,
                        spousePerson.middleName
                );
                String fullName = PersonFormatter.getFullName(spousePerson);
                spouseNode = new TreeNode(spousePerson, node.level, initials, fullName);
                nodesById.put(spousePerson.id, spouseNode);

                List<TreeNode> levelList = levelMap.computeIfAbsent(node.level, key -> new ArrayList<>());
                int index = levelList.indexOf(node);
                if (index >= 0 && index < levelList.size()) {
                    levelList.add(index + 1, spouseNode);
                } else {
                    levelList.add(spouseNode);
                }
            } else if (spouseNode.level == node.level) {
                List<TreeNode> levelList = levelMap.get(node.level);
                if (levelList != null) {
                    int personIndex = levelList.indexOf(node);
                    int spouseIndex = levelList.indexOf(spouseNode);
                    if (personIndex != -1 && spouseIndex != -1
                            && Math.abs(personIndex - spouseIndex) != 1) {
                        levelList.remove(spouseNode);
                        personIndex = levelList.indexOf(node);
                        if (personIndex == -1 || personIndex >= levelList.size()) {
                            levelList.add(spouseNode);
                        } else {
                            levelList.add(personIndex + 1, spouseNode);
                        }
                    }
                }
            }

            String key = spouseEdgeKey(person.id, spousePerson.id);
            if (spouseEdges.add(key)) {
                edges.add(new TreeEdge(person.id, spousePerson.id));
            }
        }
    }

    private void addNode(Map<Long, TreeNode> nodesById,
                         Map<Integer, List<TreeNode>> levelMap,
                         Person person,
                         int level) {
        if (person == null) {
            return;
        }
        TreeNode existing = nodesById.get(person.id);
        if (existing != null) {
            return;
        }
        String initials = InitialsUtils.buildInitials(person.lastName, person.firstName, person.middleName);
        String fullName = PersonFormatter.getFullName(person);
        TreeNode node = new TreeNode(person, level, initials, fullName);
        nodesById.put(person.id, node);
        levelMap.computeIfAbsent(level, key -> new ArrayList<>()).add(node);
    }

    private void alignChildrenBetweenParents(Map<Long, TreeNode> nodesById,
                                             Map<Long, Person> personMap) {
        Map<String, List<TreeNode>> childrenByParents = new HashMap<>();
        for (TreeNode node : nodesById.values()) {
            Person person = node.person;
            if (person == null || person.motherId == null || person.fatherId == null) {
                continue;
            }
            TreeNode motherNode = nodesById.get(person.motherId);
            TreeNode fatherNode = nodesById.get(person.fatherId);
            if (motherNode == null || fatherNode == null) {
                continue;
            }
            String key = parentPairKey(motherNode.person.id, fatherNode.person.id);
            childrenByParents.computeIfAbsent(key, k -> new ArrayList<>()).add(node);
        }

        for (Map.Entry<String, List<TreeNode>> entry : childrenByParents.entrySet()) {
            List<TreeNode> children = entry.getValue();
            if (children == null || children.size() != 1) {
                continue;
            }
            TreeNode child = children.get(0);
            Person person = child.person;
            TreeNode motherNode = nodesById.get(person.motherId);
            TreeNode fatherNode = nodesById.get(person.fatherId);
            if (motherNode == null || fatherNode == null) {
                continue;
            }
            child.x = (motherNode.x + fatherNode.x) / 2f;
        }
    }

    private String parentPairKey(long firstId, long secondId) {
        long min = Math.min(firstId, secondId);
        long max = Math.max(firstId, secondId);
        return min + ":" + max;
    }

    private String spouseEdgeKey(long firstId, long secondId) {
        long min = Math.min(firstId, secondId);
        long max = Math.max(firstId, secondId);
        return min + ":" + max;
    }

    private static class PersonLevel {
        final Person person;
        final int level;

        PersonLevel(Person person, int level) {
            this.person = person;
            this.level = level;
        }
    }
}
