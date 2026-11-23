package com.arryn.frontiermode.core;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.ModuleSignal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraftforge.event.entity.EntityEvent;

import java.util.Optional;

/**
 * {@code forge-1.20.1}'s ingress for {@code core}: Forge's {@link EntityEvent.Size} translated to
 * {@link HitboxResize}, with {@link CoreConsumers} installed. The mod entry registers {@link #build()} through a
 * lambda, once for each side (Two Lines § Module events).
 */
public final class CoreIngress {

    private CoreIngress() {
    }

    public static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(FrontierKeys.CORE_INGRESS);
        ingress.translate(EntityEvent.Size.class, HitboxResize.class, CoreIngress::resolveScalable,
                ForgeHitboxResize::new);
        ingress.consumers(CoreConsumers.declare());
        return ingress;
    }

    /** The cheap filter: an ordinary entity bounces here, before anything is built. */
    private static Optional<Entity> resolveScalable(EntityEvent.Size event) {
        Entity entity = event.getEntity();
        return EntityScales.canBeScaled(entity) ? Optional.of(entity) : Optional.empty();
    }

    /** {@link HitboxResize} over Forge's size event. */
    static final class ForgeHitboxResize extends ModuleSignal<EntityEvent.Size, Entity> implements HitboxResize {

        ForgeHitboxResize(EntityEvent.Size forgeEvent, Entity entity) {
            super(forgeEvent, entity);
        }

        @Override
        public EntityDimensions newSize() {
            return nativeEvent().getNewSize();
        }

        @Override
        public void resize(EntityDimensions size) {
            nativeEvent().setNewSize(size, true);
        }
    }
}
