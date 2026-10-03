package com.arryn.satchel.common.lifecycle;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.Objects;

/**
 * Posted to {@link SatchelEventBus} when a living entity is about to take damage, wrapping {@link
 * LivingHurtEvent} (SAT_094) -- before armor, enchantment protection, and absorption apply, so a
 * scaled amount is still reduced by armor the way an unscaled hit is. <b>The place to scale
 * damage.</b> Fires for any {@link LivingEntity}, players included; every subscriber checks its own
 * relevance.
 *
 * <p>
 * {@link #amount()} reads the live event, so it reflects any earlier subscriber's {@link
 * #setAmount}; no copy is captured. {@link #cancel()} stops the hit entirely. To read what the
 * attacker carries, use {@code MobData.readAttacker(source(), namespace)}.
 *
 * <p>
 * Producer: {@code ServerForgeIngress.onLivingHurt}. Server-only: {@code LivingEntity#hurt}
 * returns before this on the client.
 */
public final class LivingHurtSignal extends SatchelSignal<LivingHurtEvent> {

    private final LivingEntity entity;
    private final DamageSource source;

    public LivingHurtSignal(LivingHurtEvent forgeEvent) {
        super(forgeEvent);
        this.entity = Objects.requireNonNull(forgeEvent.getEntity(), "entity");
        this.source = Objects.requireNonNull(forgeEvent.getSource(), "source");
    }

    /** The entity being hurt. */
    public LivingEntity entity() {
        return entity;
    }

    /** What the damage came from; its direct entity (an arrow) and causing entity (the shooter). */
    public DamageSource source() {
        return source;
    }

    /** The damage about to be applied, before armor -- live. */
    public float amount() {
        return forgeEvent().getAmount();
    }

    public void setAmount(float amount) {
        forgeEvent().setAmount(amount);
    }
}
