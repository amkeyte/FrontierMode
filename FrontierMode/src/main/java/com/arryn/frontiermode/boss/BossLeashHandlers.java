package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;

/**
 * A Keeper chased off its post goes home (border-pregeneration.md § Movement; owner 2026-10-03). Every
 * distance-keyed system -- Sentinel curves, Environmental Tells, the compass's pad lock -- measures from
 * the boss's recorded position, so a boss that can be kited far from it pulls them all out of true. On
 * {@code BOSS_JIG}'s tick, a present boss farther than {@code bossLeashRadius} from its recorded position
 * is teleported back to it.
 */
final class BossLeashHandlers {

    private BossLeashHandlers() {
    }

    static void onBossTick(ServerLevel level, BossFixture fixture) {
        PresentBosses.forEach(level, fixture, present -> {
            Mob boss = present.boss();
            // The level 1 Keeper (the Rabbit) has its own, shorter leash.
            double radius = BossAPI.pathPositionOf(present.record()) <= 1
                    ? fixture.RULES.bossLeashRadiusFirst()
                    : fixture.RULES.bossLeashRadius();
            double radiusSq = radius * radius;
            BlockPos home = present.record().position();
            // A home chunk that isn't loaded can't be teleported into; it is tried again next tick.
            if (home == null || !boss.isAlive() || !level.isLoaded(home)) {
                return;
            }
            double x = home.getX() + 0.5;
            double z = home.getZ() + 0.5;
            if (boss.distanceToSqr(x, home.getY(), z) <= radiusSq) {
                return;
            }
            OUT.info("[Boss] leash: boss " + Ids.shortId(present.record().bossId()) + " was "
                    + (int) Math.sqrt(boss.distanceToSqr(x, home.getY(), z)) + " blocks from home (limit "
                    + (int) radius + ") -- teleporting it back to " + home.getX() + ", " + home.getY() + ", " + home.getZ());
            puff(level, boss.getX(), boss.getY(), boss.getZ());
            boss.teleportTo(x, home.getY(), z);
            // It forgets the chase, so it doesn't walk straight back out; it picks a target up again as normal.
            boss.setTarget(null);
            boss.getNavigation().stop();
            puff(level, x, home.getY(), z);
        });
    }

    private static void puff(ServerLevel level, double x, double y, double z) {
        EffectsAPI.broadcastParticle(level, ParticleTypes.PORTAL, x, y + 1.0, z, 30, 0.5, 1.0, 0.5, 0.1);
        EffectsAPI.broadcastSound(level, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
    }
}
