package com.arryn.frontiermode.siting;

import com.arryn.frontiermode.siting.server.StartingSiteHandlers;
import com.arryn.satchel.common.ingress.ModuleEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * A brand-new world's Overworld is about to choose its spawn: the module's event for the loader's spawn-position event,
 * on both lines (siting.md § When it runs). It fires once per world, ever, before any player exists, so it is unscoped:
 * a world that is still being created has no scope to resolve to. {@link StartingSiteHandlers} holds its one consumer.
 *
 * <p>{@link #claimSpawn} is the one veto. It sets the spawn and cancels vanilla's own choice together, so a consumer
 * cannot skip vanilla's choice without having set a spawn. Valid only during dispatch.
 */
public interface WorldSpawnChoosing extends ModuleEvent<Void> {

    /** The Overworld being created. */
    Level level();

    /** Whether the world was created with the bonus chest. */
    boolean bonusChestRequested();

    /**
     * Sets the world spawn to {@code site} on the level data and cancels vanilla's own choice, which also skips
     * vanilla's bonus chest: a consumer that claims the spawn places the chest itself when {@link #bonusChestRequested()}
     * says one was asked for.
     *
     * <p>It also sets the world's {@code spawnRadius} game rule to 0. Vanilla places a player by reading that rule and
     * trying the columns within that radius of the spawn, so at 0 the one column it tries is the spawn's own: every
     * player appears on the claimed spawn, on every difficulty (siting.md § Every player appears on the spawn). It is
     * written here once, at world creation, so an operator's later {@code /gamerule spawnRadius} is obeyed.
     */
    void claimSpawn(BlockPos site);
}
