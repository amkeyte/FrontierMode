package com.arryn.satchel.common.exp;

public abstract class BaseConfig {

    private final String key;
    private final Presets presets;

    protected BaseConfig(String key) {
        this.key = key;
        this.presets = createPresets();
    }

    /**
     * Subclasses must create and initialize their preset tree here.
     * Called exactly once during construction.
     */
    protected abstract Presets createPresets();

    // ─────────────────────────────────────────
    // Category access (views over shared state)
    // ─────────────────────────────────────────

    public abstract CategoryAConfig categoryA();

    // ─────────────────────────────────────────
    // Root-level config
    // ─────────────────────────────────────────

    protected Presets presets() {
        return presets;
    }

    public String key() {
        return key;
    }

    public String itemA() {
        return presets.itemA;
    } // required

    public BaseConfig itemA(String youMustSetThis) {
        presets.itemA = youMustSetThis;
        return this;
    }

    public int itemB() {
        return presets.itemB;
    }

    public BaseConfig itemB(int b) {
        presets.itemB = b;
        return this;
    }

    public String itemC() {
        return presets.itemC;
    }

    public BaseConfig itemC(String butYouCanChangeItIfYouWantTo) {
        presets.itemC = butYouCanChangeItIfYouWantTo;
        return this;
    }

    // ─────────────────────────────────────────
    // Preset root (single instance per config)
    // ─────────────────────────────────────────

    public static class Presets {
        String itemA = null; // required
        int itemB = 2;
        String itemC = "Nobody ever changes this";

        public final CategoryAConfig.CategoryAPresets categoryA =
                new CategoryAConfig.CategoryAPresets();
    }
}
