package com.arryn.frontiermode.border.server.player;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.trinket.Trinket;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;

/**
 * The {@code /border debug sickness} switch (wiki/frontiermode/architecture/border.md § Trinket ticks): a field,
 * default on, of a Trinket on {@code BORDERS_JIG}, so it ends with its scope and the next world starts with
 * sickness on. It is looked up through the Overworld's scope, which holds the only path and so is the world's,
 * as for {@code BorderAPI.PATH}. Border owns it and does not depend on Admin. Turning it off stops future
 * refreshes of the sickness effects; whatever is already granted runs down on its own duration.
 *
 * <p>{@code BORDERS_JIG} is {@code BOTH}, so a client creates one too. It acts on the server only and names no
 * type that exists on one side only.
 */
public final class SicknessSwitch implements Trinket {

    private boolean on = true;

    @Override
    public void onAttach(ScopeInfo info) {
        OUT.debug("[Border] SicknessSwitch attached");
    }

    /** Whether Frontier Sickness applies. A switch that cannot be found reads as on. */
    public static boolean isOn(MinecraftServer server) {
        return of(server).map(found -> found.on).orElse(true);
    }

    /** Sets the switch; {@code false} when it cannot be found (the Overworld's scope is not ready). */
    public static boolean set(MinecraftServer server, boolean value) {
        Optional<SicknessSwitch> found = of(server);
        found.ifPresent(switchFound -> switchFound.on = value);
        return found.isPresent();
    }

    private static Optional<SicknessSwitch> of(MinecraftServer server) {
        if (!Satchel.isReady()) {
            return Optional.empty();
        }
        return Satchel.trinket(FrontierKeys.BORDERS_JIG, new LevelScope(server.overworld()), SicknessSwitch.class);
    }
}
