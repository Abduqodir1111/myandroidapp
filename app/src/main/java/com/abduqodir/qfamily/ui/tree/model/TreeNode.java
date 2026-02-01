package com.abduqodir.qfamily.ui.tree.model;

import com.abduqodir.qfamily.data.Person;

public class TreeNode {
    public final Person person;
    public final int level;
    public float x;
    public float y;
    public final String initials;
    public final String fullName;

    public TreeNode(Person person, int level, String initials, String fullName) {
        this.person = person;
        this.level = level;
        this.initials = initials;
        this.fullName = fullName;
    }
}

