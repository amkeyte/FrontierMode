package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Result;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * Event handlers for {@code KEEPER_JIG}: the creation trigger.
 */
final class KeeperJigHandlers {

    private KeeperJigHandlers() {
    }

    /**
     * Handles {@code BORDERS_JIG}'s own {@code ScopeEvent.Loaded} (wiki/frontiermode/architecture/keeper.md § Creation).
     * Orchestrates the level's initial Border+Keeper bootstrap if it isn't seeded yet: the Keeper depends on Border,
     * never the reverse. Server and overworld only: {@code BORDERS_JIG} is {@code BOTH}, so the event fires on a
     * client's own {@code LevelJig} too.
     */
    static void onBordersScopeLoaded(ScopeEvent.Loaded event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        if (!FrontierKeys.BORDERS_JIG.equals(info.jigKey())) {
            return;
        }

        LevelScope scope = (LevelScope) info.scope();
        Level level = scope.level();

        if (!Satchel.isServer()) return;

        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        var infoOpt = BorderAPI.INFO(level);
        if (infoOpt.isEmpty()) {
            OUT.warn("[Keeper] onBordersScopeLoaded(): BordersFixture not resolvable for overworld"
                    + " level " + level.dimension().location() + " right after its own"
                    + " ScopeEvent.Loaded -- skipping the bootstrap check this cycle.");
            return;
        }

        if (infoOpt.get().seeded()) {
            return;
        }

        // The level's very first border has no natural center -- the one legitimate caller
        // of the deprecated no-center overload.
        @SuppressWarnings("deprecation")
        Result result = BorderAPI.grow(level);
        if (!result.isSuccess()) {
            OUT.warn("[Keeper] onBordersScopeLoaded(): initial grow() failed for overworld level "
                    + level.dimension().location() + ": " + result.message());
            return;
        }

        // createBoss posts KeeperCreated, which the Tell and Sentinel modules answer with their curves.
        KeeperAPI.createBoss(level, result.border());
        BorderAPI.startPregeneration(level, result.border().id());
    }
}
