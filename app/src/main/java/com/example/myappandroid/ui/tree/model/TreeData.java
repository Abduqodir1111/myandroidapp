package com.example.myappandroid.ui.tree.model;

import java.util.List;

public class TreeData {
    public final List<TreeNode> nodes;
    public final List<TreeEdge> edges;

    public TreeData(List<TreeNode> nodes, List<TreeEdge> edges) {
        this.nodes = nodes;
        this.edges = edges;
    }
}
