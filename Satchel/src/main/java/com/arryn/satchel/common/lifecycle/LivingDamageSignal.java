package com.arryn.satchel.common.lifecycle;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

import java.util.Objects;

/**
 * Posted to {@link SatchelEventBus} just before damage comes off a living entity's health,
 * wrapping {@link LivingDamageEvent} (SAT_094) -- after armor, protection, and absorption. <b>The
 * place to tally damage</b>; scale it at {@link LivingHurtSignal} instead, so armor still applies.
 *
 * <p>
 * {@link #healthRemoved()} is what this hit will actually take off: the amount, capped at the
 * entity's current health, so overkill isn't counted. Both it and {@link #amount()} read the live
 * event. Posted at Forge's lowest priority, so another mod's change to the amount is already in.
 *
 * <p>
 * Producer: {@code ServerForgeIngress.onLivingDamage}. Server-only, like {@link LivingHurtSignal}.
 */
public final class LivingDamageSignal extends SatchelSignal<LivingDamageEvent> {

    private final LivingEntity entity;
    private final DamageSource source;

    public LivingDamageSignal(LivingDamageEvent forgeEvent) {
        super(forgeEvent);
        this.entity = Objects.requireNonNull(forgeEvent.getEntity(), "entity");
        this.source = Objects.requireNonNull(forgeEvent.getSource(), "source");
    }

    /** The entity taking the damage. */
    public LivingEntity entity() {
        return entity;
    }

    /** What the damage came from. */
    public DamageSource source() {
        return source;
    }

    /** The damage about to come off health, after armor and absorption -- live. */
    public float amount() {
        return forgeEvent().getAmount();
    }

    public void setAmount(float amount) {
        forgeEvent().setAmount(amount);
    }

    /** The health this hit actually removes: {@link #amount()} capped at the entity's current health. */
    public float healthRemoved() {
        return Math.max(0f, Math.min(amount(), entity.getHealth()));
    }
}
