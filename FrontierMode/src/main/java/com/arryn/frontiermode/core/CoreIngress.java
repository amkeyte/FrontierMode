package com.arryn.frontiermode.core;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.entity.EntityEvent;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The holder class for {@code core}'s ingress: the hitbox-scale module event and its one consumer, which scales the new
 * size of an entity a {@link ScaleSource} claims (wiki/frontiermode/architecture/core.md § Registration).
 * {@link CoreModule#init()} registers {@link #build()} through a lambda, once for each side. The render scale is not
 * here: it is a {@code PoseStack} scale around the vanilla render, a direct touch on the render events.
 */
final class CoreIngress {

    // Vanilla's mob step height, in blocks.
    private static final float VANILLA_STEP_HEIGHT = 0.6F;

    private CoreIngress() {
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.CORE_INGRESS);
        AtomicBoolean logged = new AtomicBoolean();
        ingress.translate(EntityEvent.Size.class, CoreIngress::resolveScalable, HitboxResize::new);
        ingress.listenForAll(HitboxResize.class, event -> onResize(event, logged));
        return ingress;
    }

    /** The cheap filter: an ordinary entity bounces here, before anything is built. */
    private static Optional<Entity> resolveScalable(EntityEvent.Size event) {
        Entity entity = event.getEntity();
        return EntityScales.canBeScaled(entity) ? Optional.of(entity) : Optional.empty();
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
            // steps too. Never lowers one, so it is safe on every dimension refresh. It is not saved and
            // is not a jump: vanilla has no jump-height attribute for mobs.
            entity.setMaxUpStep(Math.max(entity.maxUpStep(), VANILLA_STEP_HEIGHT * scale));
        }
    }
}
