package com.arryn.frontiermode.border.server.rules;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.BordersRevisionMonitor;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import net.minecraftforge.fml.LogicalSide;


public class BordersTriggers {

    private static final BordersRevisionMonitor FINDER_ITEMS_UPDATE_MONITOR =
            new BordersRevisionMonitor(5);

    public static BorderRules RULES = BorderRules.ACTIVE;

    public static void updateFinderItems(ScopeEvent.Tick event) {
        if (Satchel.require().side() == LogicalSide.CLIENT) return;
        if (!(event.info().scope() instanceof LevelScope levelScope)) return;

        var level = levelScope.level();
        if (!FINDER_ITEMS_UPDATE_MONITOR.poll(level)) return;
        RULES.updateFinderItems(level);
    }
}
