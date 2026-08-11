package com.arryn.satchel.common.jig.model;

import com.arryn.satchel.common.identity.JigKey;

public final class ModelSource {

    private final String name;
    private final int index;
    private final boolean active;
    private final JigKey<ModelJig> key;

    public ModelSource(String name, int index, boolean active, JigKey<ModelJig> key) {
        this.name = name;
        this.index = index;
        this.active = active;
        this.key = key;
    }

    public String name() { return name; }
    public int index() { return index; }
    public boolean active() { return active; }
    public JigKey<ModelJig> key(){return key;};
}