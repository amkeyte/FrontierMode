package com.arryn.frontiermode.border.common.fixture;

import net.minecraft.core.BlockPos;

import java.util.List;

public final class BordersRulesFacet {
    private final BordersFixture setting;

    BordersRulesFacet(BordersFixture fixture) {
        this.setting = fixture;
    }

    public List<Border> containing(BlockPos pos) {
        return setting.logic.containing(setting.all(), pos);
    }
}
