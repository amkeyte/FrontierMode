package com.arryn.satchel.common.exp;

public class HomeConfig extends BaseConfig {

    private final HomeCategoryAConfig categoryA;

    public HomeConfig(String key) {
        super(key);
        this.categoryA = new HomeCategoryAConfig(presets().categoryA);
    }

    @Override
    protected Presets createPresets() {
        return new HomePresets();
    }

    @Override
    public CategoryAConfig categoryA() {
        return categoryA;
    }

    private static class HomePresets extends Presets {
        HomePresets() {
            itemB = 42;
            categoryA.paramB = 42;
        }
    }
}
