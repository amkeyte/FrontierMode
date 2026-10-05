package com.arryn.frontiermode.diorite.common;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.diorite.DioriteEntities;
import com.arryn.frontiermode.diorite.DioriteItems;
import com.arryn.frontiermode.diorite.server.rules.DioriteRules;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * The thrown Edge Stone (diorite-tools.md § The Edge Stone): poofs the instant it crosses the
 * Frontier line, in flight. The crossing test is server-side only -- the first server tick records
 * whether the stone is outside the Frontier, and a later change of that state is the crossing, in
 * either direction. The state is not saved. A stone that hits something first just breaks, as a
 * snowball does, with no damage and no drop.
 */
public final class EdgeStone extends ThrowableItemProjectile {

    /** Vanilla snowball's entity event for "show break particles". */
    private static final byte BREAK_EVENT = 3;

    /** Whether the first server tick was outside the Frontier; null until that tick. */
    private Boolean startedOutside;

    public EdgeStone(EntityType<? extends EdgeStone> type, Level level) {
        super(type, level);
    }

    public EdgeStone(Level level, LivingEntity thrower) {
        super(DioriteEntities.EDGE_STONE.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return DioriteItems.EDGE_STONE.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || isRemoved() || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean outside = BorderAPI.frontierDistanceAt(level(), blockPosition()) > 0;
        if (startedOutside == null) {
            startedOutside = outside;
        } else if (outside != startedOutside) {
            poof(serverLevel);
        }
    }

    private void poof(ServerLevel level) {
        EffectsAPI.broadcastParticle(level, ParticleTypes.POOF, getX(), getY(), getZ(),
                DioriteRules.ACTIVE.edgeStonePoofParticles(), 0.2, 0.2, 0.2, 0.02);
        EffectsAPI.broadcastSound(level, getX(), getY(), getZ(),
                SoundEvents.TURTLE_EGG_CRACK, SoundSource.NEUTRAL, 1.0F, 1.0F);
        discard();
    }

    /** Hits a block or an entity: breaks with vanilla's item-break particles, no damage, no drop. */
    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            level().broadcastEntityEvent(this, BREAK_EVENT);
            discard();
        }
    }

    @Override
    public void handleEntityEvent(byte event) {
        if (event == BREAK_EVENT) {
            ParticleOptions particle = new ItemParticleOption(ParticleTypes.ITEM, getItem());
            for (int i = 0; i < 8; i++) {
                level().addParticle(particle, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
            }
        } else {
            super.handleEntityEvent(event);
        }
    }
}
