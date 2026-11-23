package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.BordersCrudFacet;
import com.arryn.frontiermode.border.common.fixture.BordersPathFacet;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.trinket.Ticking;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The path-reconciliation check (wiki/frontiermode/architecture/keeper.md § Path reconciliation): a Trinket on
 * {@code KEEPER_JIG}'s tick that derives everything from state and keeps none. It computes the path's set of
 * {@code layer()} values and hands it to the Keeper fixture, which compares it with its own records' layers and logs a
 * gap. One-directional only: it catches a path Border with no Keeper record, and stops there. Server only: the jig is
 * {@code BOTH}, so the Trinket exists on a client too and returns there.
 */
final class PathReconciliation implements Ticking {

    @Override
    public void onTickAfter(ScopeInfo info) {
        if (!Satchel.isServer()) {
            return;
        }
        LevelScope scope = info.scopeAs();
        Level level = scope.level();

        Optional<BordersPathFacet> pathOpt = BorderAPI.PATH(level);
        Optional<BordersCrudFacet> crudOpt = BorderAPI.CRUD(level);
        if (pathOpt.isEmpty() || crudOpt.isEmpty()) {
            return;
        }
        BordersPathFacet path = pathOpt.get();
        BordersCrudFacet crud = crudOpt.get();

        Map<Integer, UUID> pathLayers = new HashMap<>();
        for (UUID id : path.all()) {
            crud.get(id).ifPresent(b -> pathLayers.put(b.layer(), b.id()));
        }

        KeeperAPI.keepers(level).ifPresent(keepers -> keepers.reportMismatch(level, pathLayers));
    }
}
