package com.arryn.frontiermode.difficulty;

import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

import java.util.Objects;

/**
 * A mob is joining a level for the first time: the module's event for Forge's {@link EntityJoinLevelEvent}
 * (difficulty.md § When it's set). Its context is the mob. The ingress's resolver passes only a fresh join
 * of a {@link Mob}, so a chunk reload or a non-mob entity is dropped before an event is built, and the
 * ingress is {@code SERVER}, so a join on the client's thread never reaches it. {@link DifficultyHandlers#onMobJoining} is its one consumer.
 *
 * <p>It offers no veto. Valid only during dispatch: after the consumer returns, a read of the native event
 * throws, and the level copied at construction stays readable.
 */
public final class MobJoining extends ModuleSignal<EntityJoinLevelEvent, Mob> {

    private final Level level;

    MobJoining(EntityJoinLevelEvent forgeEvent, Mob mob) {
        super(forgeEvent, mob);
        this.level = Objects.requireNonNull(forgeEvent.getLevel(), "level");
    }

    /** The mob that is joining the level. */
    public Mob mob() {
        return context();
    }

    /** The level it is joining. */
    public Level level() {
        return level;
    }
}
