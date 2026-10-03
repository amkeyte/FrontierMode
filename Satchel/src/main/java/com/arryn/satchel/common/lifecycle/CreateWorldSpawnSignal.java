package com.arryn.satchel.common.lifecycle;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.event.level.LevelEvent;

import java.util.Objects;

/**
 * Posted to {@link SatchelEventBus} when a brand-new world's overworld is about to pick its spawn,
 * wrapping {@link LevelEvent.CreateSpawnPosition} (SAT_093). Fires once per world, ever: only for a
 * world that has never been initialized, only for the overworld, never for a debug world.
 * Reopening a world never posts it.
 *
 * <p>
 * <b>{@link #cancel()} replaces vanilla's choice entirely</b> -- it skips vanilla's spawn search
 * <em>and</em> its bonus chest. A subscriber that cancels must call {@code
 * levelData().setSpawn(...)} itself, and place the bonus chest itself when {@link
 * #bonusChestRequested()} says the player asked for one. Canceling without moving the spawn leaves
 * it at the level data's default (about 0,0,0); the producer logs a warning when that happens.
 *
 * <p>
 * <b>When it fires, the world is half awake.</b> It runs on the server thread, inside {@code
 * MinecraftServer.createLevels}, after the overworld's {@code LevelEvent.Load} and before any
 * other dimension exists or any tick has run:
 * <ul>
 *   <li>Satchel's server foundation is bound and the overworld's scope is introduced, but no scope
 *   has reached {@code Loaded} and no fixture is hydrated -- a subscriber may read terrain,
 *   Tunables, and TuningTables, never scope or fixture state.</li>
 *   <li>Tunables are loaded (from Satchel's common setup and again just before this server
 *   started), so a subscriber reads the CSV's values.</li>
 *   <li>A subscriber may load or generate chunks synchronously -- vanilla's own search loads up to
 *   121 here. Generated chunks are real and saved; mobs generated with them post spawn signals
 *   into a Satchel whose scopes aren't ready yet.</li>
 *   <li>A subscriber that throws fails world creation ("Exception initializing level").</li>
 * </ul>
 *
 * <p>
 * {@code level} is typed as the side-agnostic {@link Level}, matching {@link MobDied}'s
 * convention; it is always the overworld {@code ServerLevel}.
 *
 * <p>
 * Producer: {@code ServerForgeIngress.onCreateWorldSpawn} (SAT_093).
 */
public final class CreateWorldSpawnSignal extends SatchelSignal<LevelEvent.CreateSpawnPosition> {

    private final Level level;
    private final ServerLevelData levelData;
    private final boolean bonusChestRequested;

    public CreateWorldSpawnSignal(Level level, LevelEvent.CreateSpawnPosition forgeEvent, boolean bonusChestRequested) {
        super(forgeEvent);
        this.level = Objects.requireNonNull(level, "level");
        this.levelData = Objects.requireNonNull(forgeEvent.getSettings(), "levelData");
        this.bonusChestRequested = bonusChestRequested;
    }

    /** The overworld being created. */
    public Level level() {
        return level;
    }

    /** The overworld's level data -- {@code setSpawn(pos, angle)} here sets the world spawn. */
    public ServerLevelData levelData() {
        return levelData;
    }

    /** Whether the player asked for a bonus chest when creating the world. */
    public boolean bonusChestRequested() {
        return bonusChestRequested;
    }
}
