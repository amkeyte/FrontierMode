package com.arryn.satchel.common.lifecycle;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingConversionEvent;

import java.util.Objects;

/**
 * Posted to {@link SatchelEventBus} when a mob is about to convert into another type (a Zombie
 * drowning into a Drowned, a Zombified Piglin conversion, a Husk and the like), wrapping
 * {@link LivingConversionEvent.Pre} (SAT_101). {@link #cancel()} keeps the mob as it is.
 *
 * <p>
 * This is the "before" half of conversion; MobData's copy to the new mob rides the separate
 * {@code LivingConversionEvent.Post}. A conversion that calls {@code Mob#convertTo} directly without
 * the Forge Pre check (a Skeleton freezing into a Stray) never posts this signal.
 *
 * <p>
 * Producer: {@code ServerForgeIngress.onLivingConversionPre}. Server-side.
 */
public final class LivingConversionSignal extends SatchelSignal<LivingConversionEvent.Pre> {

    private final LivingEntity entity;
    private final EntityType<? extends LivingEntity> outcome;

    public LivingConversionSignal(LivingConversionEvent.Pre forgeEvent) {
        super(forgeEvent);
        this.entity = Objects.requireNonNull(forgeEvent.getEntity(), "entity");
        this.outcome = Objects.requireNonNull(forgeEvent.getOutcome(), "outcome");
    }

    /** The mob that would convert. */
    public LivingEntity entity() {
        return entity;
    }

    /** The type it would become. */
    public EntityType<? extends LivingEntity> outcome() {
        return outcome;
    }
}
