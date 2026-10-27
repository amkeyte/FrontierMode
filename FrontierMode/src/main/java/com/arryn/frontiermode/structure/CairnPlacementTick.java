package com.arryn.frontiermode.structure;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.structure.common.CairnFixture;
import com.arryn.frontiermode.structure.common.CairnRecord;
import com.arryn.frontiermode.structure.common.StructureBundle;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.level.Level;

/**
 * Cairn placement work, on {@code STRUCTURE_JIG}'s own tick: one candidate spot per unplaced Cairn per
 * tick, tried by {@link CairnPlacer}. Keeps no state of its own. It runs after every jig's internal work
 * in the tick, and a write it makes is saved on the next execution pulse.
 */
public final class CairnPlacementTick implements Ticking {

    @Override
    public void onAttach(ScopeInfo info) {
        OUT.debug("[Structure] CairnPlacementTick attached to " + info.debugName());
    }

    @Override
    public void onTickAfter(ScopeInfo info) {
        LevelScope scope = info.scopeAs();
        Level level = scope.level();

        StructureBundle bundle = info.getOrCreate(FrontierKeys.STRUCTURE_BUNDLE);
        CairnFixture fixture = bundle.getOrCreateFixture(FrontierKeys.CAIRN, CairnFixture::new);

        for (CairnRecord record : fixture.all()) {
            if (record.placed()) {
                continue;
            }
            CairnPlacer.attemptPlacement(level, fixture, record);
        }
    }
}
