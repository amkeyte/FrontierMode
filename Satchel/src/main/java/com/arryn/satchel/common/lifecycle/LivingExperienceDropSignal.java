package com.arryn.satchel.common.lifecycle;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;

import java.util.Objects;

/**
 * Posted to {@link SatchelEventBus} when a dying entity is about to drop experience — before the
 * orb is actually awarded, so a subscriber can {@link #cancel()} to suppress it entirely (per
 * {@link LivingExperienceDropEvent}'s own doc: canceling prevents dropping experience).
 *
 * <p>
 * Deliberately does <em>not</em> expose a captured {@code droppedExperience} accessor the way
 * {@link ItemPickupSignal}/{@link BlockBreakSignal} capture their own fields — {@code
 * droppedExperiencePoints} is a mutable primitive on the wrapped event ({@code
 * setDroppedExperience(int)}), so a field copied at construction would go stale the moment an
 * earlier subscriber changes it, silently showing a later subscriber the wrong value. Read or
 * adjust the amount through {@link #forgeEvent()} directly, which is always live.
 *
 * <p>
 * Posted once, unscoped, to the whole bus — every subscriber checks its own relevance before
 * acting, same discipline {@link MobDied} established.
 *
 * <p>
 * Producer: {@code ServerForgeIngress.onLivingExperienceDrop} (SAT_072).
 */
public final class LivingExperienceDropSignal extends SatchelSignal<LivingExperienceDropEvent> {

    private final LivingEntity entity;

    public LivingExperienceDropSignal(LivingExperienceDropEvent forgeEvent) {
        super(forgeEvent);
        this.entity = Objects.requireNonNull(forgeEvent.getEntity(), "entity");
    }

    /** The entity dropping experience. */
    public LivingEntity entity() {
        return entity;
    }
}
