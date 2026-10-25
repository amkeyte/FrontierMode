package com.arryn.satchel.common.tracking;

import com.arryn.satchel.common.util.MobData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * The mark SatchelHealth puts on its canary (SAT_113): one {@code MobData} namespace, saved with the
 * mob. It survives a restart, which is what lets the next boot find and discard the previous run's
 * canary instead of leaving one more persistent bat at spawn, and it is what the unscoped canary
 * event tests ({@link CanaryEventHealthCheck}). Server only: a client mob has no container, so it is
 * never a canary here.
 */
final class CanaryData {

    private CanaryData() {}

    /** Not Satchel's own namespace, which {@code MobData} refuses. */
    static final ResourceLocation NAMESPACE = new ResourceLocation("satchelhealth", "canary");

    /** Marks {@code mob} as a canary. */
    static void mark(Mob mob) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("canary", true);
        MobData.write(mob, NAMESPACE, tag);
    }

    /** Whether {@code entity} is a mob carrying the canary mark. */
    static boolean isCanary(Entity entity) {
        return entity instanceof Mob mob && MobData.has(mob, NAMESPACE);
    }
}
