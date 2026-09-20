package com.arryn.satchel.common.lifecycle;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import java.util.Collection;
import java.util.Objects;

/**
 * Posted to {@link SatchelEventBus} when a dying entity is about to drop items — before the drops
 * are actually spawned into the world, so a subscriber can {@link #cancel()} to suppress them
 * entirely (per {@link LivingDropsEvent}'s own doc: canceling means the entity drops nothing), or
 * mutate {@link #drops()} directly instead.
 *
 * <p>
 * {@code drops()} returns the same live {@link Collection} the wrapped {@link LivingDropsEvent}
 * holds, not a copy — safe to expose directly (unlike a mutable primitive field) since it's a
 * stable reference the whole event lifetime; mutating it here is visible to whichever code spawns
 * the drops afterward, same as mutating {@code forgeEvent().getDrops()} directly would be.
 *
 * <p>
 * Posted once, unscoped, to the whole bus — every subscriber checks its own relevance before
 * acting, same discipline {@link MobDied} established.
 *
 * <p>
 * Producer: {@code ServerForgeIngress.onLivingDrops} (SAT_072).
 */
public final class LivingDropsSignal extends SatchelSignal<LivingDropsEvent> {

    private final LivingEntity entity;
    private final Collection<ItemEntity> drops;

    public LivingDropsSignal(LivingDropsEvent forgeEvent) {
        super(forgeEvent);
        this.entity = Objects.requireNonNull(forgeEvent.getEntity(), "entity");
        this.drops = Objects.requireNonNull(forgeEvent.getDrops(), "drops");
    }

    /** The entity dropping items. */
    public LivingEntity entity() {
        return entity;
    }

    /** The live, mutable collection of items about to be spawned -- not a copy. */
    public Collection<ItemEntity> drops() {
        return drops;
    }
}
