package com.arryn.frontiermode.bosstell.common.entities;

import com.arryn.frontiermode.bosstell.BossTellRegisters;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.level.Level;

/**
 * The thrown Amethyst Eye (amethyst-eye.md § The flight is vanilla's): vanilla's {@link EyeOfEnder} under its
 * own entity type, so it drops and renders as the Amethyst Eye. {@code signalTo} does the whole flight, as it does
 * for vanilla's Eye, so the range, the lifetime and the odds of a drop are vanilla's and none of them is written
 * here. It holds no state and ticks nothing of its own. The one change is the end-of-flight sound: vanilla plays
 * {@code ENDER_EYE_DEATH}, and this plays the amethyst chime in its place.
 *
 * <p>The whole class is a subclass of a vanilla entity, which is why it carries the marker.
 */
@PlatformBound(reason = "entity")
public final class AmethystEye extends EyeOfEnder {

    /** For the entity type's factory. */
    public AmethystEye(EntityType<? extends AmethystEye> type, Level level) {
        super(type, level);
    }

    /**
     * At a position, as {@code EnderEyeItem} makes vanilla's. Vanilla's constructor of this shape binds
     * {@code EntityType.EYE_OF_ENDER}, so this one binds the Amethyst Eye's own type.
     */
    public AmethystEye(Level level, double x, double y, double z) {
        this(BossTellRegisters.AMETHYST_EYE_ENTITY.get(), level);
        setPos(x, y, z);
    }

    /**
     * Vanilla's end-of-flight sound becomes the amethyst chime, whether the Eye then drops or shatters. Any other
     * sound this entity makes (a splash on entering water) is vanilla's.
     */
    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        super.playSound(sound == SoundEvents.ENDER_EYE_DEATH ? SoundEvents.AMETHYST_BLOCK_CHIME : sound, volume, pitch);
    }
}
