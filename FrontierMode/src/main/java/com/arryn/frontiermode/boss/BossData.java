package com.arryn.frontiermode.boss;

import com.arryn.satchel.common.platform.PlatformBound;
import com.arryn.satchel.common.util.MobData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/**
 * A boss's body carries the damage tally (boss.md § What counts as a defeat): the {@code frontiermode:boss}
 * MobData namespace holding one number, {@code playerDamage}, the health players' own hits have removed from this
 * body less what has healed back. It is the durable copy of the working tally on {@link Boss}: written whenever
 * the working copy changes and read back when the body attaches, so a chunk unload, a quit and a restart keep it.
 * It is saved with the body and dies with it, so no record outside the body can go stale. A new body has no
 * namespace, and a body with none reads as zero.
 *
 * <p>Server-only: MobData is not synced. This class is the one place the tag is touched, so {@code Boss} and
 * {@code Recovery} see a number and never a tag.
 */
@PlatformBound(reason = "mob data namespace")
public final class BossData {

    public static final ResourceLocation NAMESPACE = new ResourceLocation("frontiermode", "boss");

    private static final String KEY_PLAYER_DAMAGE = "playerDamage";

    private BossData() {
    }

    /** The tally saved with {@code mob}, or zero for a body that carries none. */
    public static double playerDamage(Mob mob) {
        return MobData.read(mob, NAMESPACE).getDouble(KEY_PLAYER_DAMAGE);
    }

    /** Saves the tally with {@code mob}, replacing what it held. */
    public static void writePlayerDamage(Mob mob, double playerDamage) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(KEY_PLAYER_DAMAGE, playerDamage);
        MobData.write(mob, NAMESPACE, tag);
    }
}
