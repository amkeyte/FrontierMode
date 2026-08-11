package com.arryn.satchel.common.exp;

public abstract class CategoryAConfig {

    protected CategoryAConfig() {}

    protected abstract CategoryAPresets presets();

    public String paramA() {
        return presets().paramA;
    } // required

    public CategoryAConfig paramA(String youMustSetThis) {
        presets().paramA = youMustSetThis;
        return this;
    }

    public int paramB() {
        return presets().paramB;
    }

    public CategoryAConfig paramB(int b) {
        presets().paramB = b;
        return this;
    }

    public String paramC() {
        return presets().paramC;
    }

    public CategoryAConfig paramC(String butYouCanChangeItIfYouWantTo) {
        presets().paramC = butYouCanChangeItIfYouWantTo;
        return this;
    }

    // ─────────────────────────────────────────
    // Category preset node (not a root)
    // ─────────────────────────────────────────

    public static class CategoryAPresets {
        String paramA = null; // required
        int paramB = 2;
        String paramC = "Nobody ever changes this";
    }
}
