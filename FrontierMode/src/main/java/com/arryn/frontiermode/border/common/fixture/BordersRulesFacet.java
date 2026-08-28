package com.arryn.frontiermode.border.common.fixture;

import com.arryn.frontiermode.border.common.BorderMath;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class BordersRulesFacet {
    private final BordersFixture setting;

    BordersRulesFacet(BordersFixture fixture) {
        this.setting = fixture;
    }

    /**
     * FRO_047: inlined from the deleted {@code BorderLogic.containing(List<Border>, BlockPos)} --
     * that method's only caller already always passed {@code setting.all()}, so the ownership
     * check it did (redundant against this fixture's own borders) is dropped along with it.
     */
    public List<Border> containing(BlockPos pos) {
        List<Border> all = setting.all();

        if (pos == null || all.isEmpty()) {
            return List.of();
        }

        List<Border> result = new ArrayList<>();
        for (Border b : all) {
            if (BorderMath.isInside(b, pos)) {
                result.add(b);
            }
        }

        return result;
    }
}
