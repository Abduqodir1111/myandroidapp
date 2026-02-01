package com.example.myappandroid.ui.tree.model;

public class TreeEdge {
    public final long fromId;
    public final long toId;

    public TreeEdge(long fromId, long toId) {
        this.fromId = fromId;
        this.toId = toId;
    }
}
