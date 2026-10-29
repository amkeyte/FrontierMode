package com.arryn.frontiermode.siting;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;

import java.util.Objects;

/**
 * A brand-new world's Overworld is about to choose its spawn: the module's event for Forge's
 * {@link LevelEvent.CreateSpawnPosition} (siting.md § When it runs). It fires once per world, ever, before
 * any player exists, so it is unscoped: a world that is still being created has no scope to resolve to.
 * {@link SitingIngress} holds its one consumer.
 *
 * <p>{@link #claimSpawn} is the one veto. It sets the spawn and cancels vanilla's own choice together, so a
 * consumer cannot skip vanilla's choice without having set a spawn. Valid only during dispatch: after the
 * consumer returns, a read of the native event throws, and the two facts copied at construction stay
 * readable.
 */
public final class WorldSpawnChoosing extends ModuleSignal<LevelEvent.CreateSpawnPosition, Void> {

    private final Level level;
    private final boolean bonusChestRequested;

    WorldSpawnChoosing(LevelEvent.CreateSpawnPosition forgeEvent) {
        super(forgeEvent, null);
        this.level = (Level) Objects.requireNonNull(forgeEvent.getLevel(), "level");
        // The Forge event does not say whether the world was created with the bonus chest; the server's
        // world options do.
        this.bonusChestRequested = Objects.requireNonNull(level.getServer(), "server")
                .getWorldData().worldGenOptions().generateBonusChest();
    }

    /** The Overworld being created. */
    public Level level() {
        return level;
    }

    /** Whether the world was created with the bonus chest. */
    public boolean bonusChestRequested() {
        return bonusChestRequested;
    }

    /**
     * Sets the world spawn to {@code site} on the level data and cancels vanilla's own choice, which also
     * skips vanilla's bonus chest: a consumer that claims the spawn places the chest itself when
     * {@link #bonusChestRequested()} says one was asked for.
     */
    public void claimSpawn(BlockPos site) {
        LevelEvent.CreateSpawnPosition event = nativeEvent();
        event.getSettings().setSpawn(site, 0.0F);
        event.setCanceled(true);
    }
}
