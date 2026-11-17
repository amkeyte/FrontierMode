package com.arryn.frontiermode.sentinel;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

import java.util.Objects;
import java.util.Optional;

/**
 * A mob that carries the Sentinel record is joining a level, fresh or loaded from disk: the Sentinel module's event for
 * Forge's {@link EntityJoinLevelEvent} (sentinels.md § Presence). Its context is the mob. The ingress's resolver passes
 * only a mob that is a Sentinel candidate and carries the record, so every other entity is dropped before an event is
 * built, and the ingress is {@code SERVER}, so a join on the client's thread never reaches it.
 * {@link SentinelJigHandlers#onSentinelJoining} is its one consumer.
 *
 * <p>It offers no veto. Valid only during dispatch: after the consumer returns, a read of the native event throws, and
 * the level and the load flag copied at construction stay readable.
 */
public final class SentinelJoining extends ModuleSignal<EntityJoinLevelEvent, Mob> {

    /** The Forge event this module event reflects, for the ingress's translate. */
    static final Class<EntityJoinLevelEvent> NATIVE = EntityJoinLevelEvent.class;

    private final Level level;
    private final boolean loadedFromDisk;

    SentinelJoining(EntityJoinLevelEvent forgeEvent, Mob mob) {
        super(forgeEvent, mob);
        this.level = Objects.requireNonNull(forgeEvent.getLevel(), "level");
        this.loadedFromDisk = forgeEvent.loadedFromDisk();
    }

    /**
     * The translate's resolver, the cheap filter for a join, which fires for every entity: the mob for a Sentinel
     * candidate that carries the Sentinel record, empty for anything else.
     */
    static Optional<Mob> resolve(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Mob mob && SentinelMarkers.isCandidate(mob) && SentinelData.has(mob)) {
            return Optional.of(mob);
        }
        return Optional.empty();
    }

    /** The Sentinel that is joining the level. */
    public Mob mob() {
        return context();
    }

    /** The level it is joining. */
    public Level level() {
        return level;
    }

    /** True for a mob rehydrated with its chunk, false for one that has just come into being. */
    public boolean loadedFromDisk() {
        return loadedFromDisk;
    }
}
