package com.arryn.satchel.common.lifecycle;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Posted to {@link SatchelEventBus} when a mob is about to change its attack target, wrapping
 * {@link LivingChangeTargetEvent} (SAT_094). Covers both target paths vanilla has: a {@code Mob}'s
 * own {@code setTarget} (goal-driven AI) and brain-driven behaviors.
 *
 * <p>
 * {@link #cancel()} keeps the mob's current target; {@link #setNewTarget} redirects it ({@code
 * null} clears it). {@link #newTarget()} reads the live event, so it reflects an earlier
 * subscriber's redirect.
 *
 * <p>
 * Producer: {@code ServerForgeIngress.onLivingChangeTarget}. Server-side; a client-side firing is
 * dropped at ingress.
 */
public final class LivingChangeTargetSignal extends SatchelSignal<LivingChangeTargetEvent> {

    private final LivingEntity entity;
    @Nullable
    private final LivingEntity originalTarget;

    public LivingChangeTargetSignal(LivingChangeTargetEvent forgeEvent) {
        super(forgeEvent);
        this.entity = Objects.requireNonNull(forgeEvent.getEntity(), "entity");
        this.originalTarget = forgeEvent.getOriginalTarget();
    }

    /** The mob changing its target. */
    public LivingEntity entity() {
        return entity;
    }

    /** The target the mob asked for, before any subscriber redirected it; {@code null} to clear. */
    @Nullable
    public LivingEntity originalTarget() {
        return originalTarget;
    }

    /** The target the mob will take -- live. */
    @Nullable
    public LivingEntity newTarget() {
        return forgeEvent().getNewTarget();
    }

    public void setNewTarget(@Nullable LivingEntity target) {
        forgeEvent().setNewTarget(target);
    }
}
