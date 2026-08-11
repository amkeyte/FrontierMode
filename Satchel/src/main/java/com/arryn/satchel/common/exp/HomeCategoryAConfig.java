package com.arryn.satchel.common.exp;

public class HomeCategoryAConfig extends CategoryAConfig {

    private final CategoryAPresets presets;

    public HomeCategoryAConfig(CategoryAPresets presets) {
        this.presets = presets;
    }

    @Override
    protected CategoryAPresets presets() {
        return presets;
    }
}
