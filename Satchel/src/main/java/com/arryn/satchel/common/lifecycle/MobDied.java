package com.arryn.satchel.common.lifecycle;

import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import java.util.Objects;
import java.util.UUID;

/**
 * Posted to {@link com.arryn.satchel.common.lifecycle.SatchelEventBus} when a
 * {@link LivingDeathEvent} fires for a UUID that at least one registered
 * {@link com.arryn.satchel.common.jig.mob.MobInterestRegistry} supplier currently
 * has interest in.
 *
 * <p>
 * Deliberately <em>not</em> a {@link ScopeEvent} — {@code ScopeEvent}'s contract
 * assumes an always-resolved {@code ScopeInfo}, but a boss mob spends most of its
 * life with no attached scope, and even mid-reentry the scope doesn't reattach until
 * the next poll tick. Deaths in that window are routine, not edge cases.
 *
 * <p>
 * Posted once, unscoped, to the whole bus — every subscriber checks its own
 * relevance before acting, exactly as the raw {@link LivingDeathEvent} listener
 * this replaces already required.
 *
 * <p>
 * Producer: {@code ServerForgeIngress.onLivingDeath} (SAT_044).
 */
public final class MobDied {

    private final Level level;
    private final UUID uuid;
    private final LivingDeathEvent forgeEvent;

    public MobDied(Level level, UUID uuid, LivingDeathEvent forgeEvent) {
        this.level = Objects.requireNonNull(level, "level");
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.forgeEvent = Objects.requireNonNull(forgeEvent, "forgeEvent");
    }

    /** The level the mob died in. */
    public Level level() {
        return level;
    }

    /** The mob's persistent UUID. */
    public UUID uuid() {
        return uuid;
    }

    /** The raw Forge death event, for consumers that need entity details. */
    public LivingDeathEvent forgeEvent() {
        return forgeEvent;
    }
}
