package com.arryn.frontiermode.core;

import com.arryn.satchel.common.ingress.GlobalConsumers;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@code core}'s global consumers, written once for both lines: the hitbox-scale consumer, which scales the new size of
 * an entity a {@link ScaleSource} claims (wiki/frontiermode/architecture/core.md § Registration). Each line's
 * {@code CoreIngress} translates the loader's event to {@link HitboxResize} and installs these. The render scale is not
 * here: it is a {@code PoseStack} scale around the vanilla render, a direct touch on the render events.
 */
public final class CoreConsumers {

    // Vanilla's mob step height, in blocks.
    private static final float VANILLA_STEP_HEIGHT = 0.6F;

    private CoreConsumers() {
    }

    /** A fresh set of consumers: build one in the ingress's side-keyed factory. */
    public static GlobalConsumers declare() {
        GlobalConsumers declared = new GlobalConsumers();
        AtomicBoolean logged = new AtomicBoolean();
        declared.listenForAll(HitboxResize.class, event -> onResize(event, logged));
        return declared;
    }

    /**
     * Scales the new size with the eye height, and applies the step-up rule. The scale comes from
     * {@link EntityScales}, which each source answers from a lookup that server and client agree on.
     */
    private static void onResize(HitboxResize event, AtomicBoolean logged) {
        Entity entity = event.entity();
        float scale = EntityScales.scaleOf(entity);
        if (logged.compareAndSet(false, true)) {
            OUT.debug("[Core] onResize(): the hitbox-scale consumer fired for the first time, for "
                    + EntityType.getKey(entity.getType()) + " at scale " + scale);
        }
        if (scale != 1.0F) {
            event.resize(event.newSize().scale(scale));
        }
        if (scale > 1.0F) {
            // A mob that is bigger should clear what its size clears: vanilla's 0.6 step height is for a
            // 1-block-wide, 2-tall body. The step also drives pathfinding, so a big mob plans over taller
            // steps too. Never lowers one, so it is safe on every dimension refresh. It is not a jump: vanilla has no
            // jump-height attribute for mobs.
            FrontierLine.raiseStepHeight(entity, VANILLA_STEP_HEIGHT * scale);
        }
    }
}
