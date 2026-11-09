package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.core.WidePathing;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.tunable.Tunable;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * A boss chased off its post goes home (border-pregeneration.md § Movement; owner 2026-10-03). Every distance-keyed
 * system -- Sentinel curves, Environmental Tells, the compass's pad lock -- measures from the boss's recorded position,
 * so a boss that can be kited far from it pulls them all out of true. Each tick, a boss farther than its radius from its
 * recorded position is teleported back to it.
 *
 * <p>The radius is a setting the kind passes at subscribe (the Keeper passes its own {@code bossLeashRadiusFirst} for
 * path position 1 or below, else {@link #standardRadius()}). The tick also advances a wide boss's path (FRO_224).
 */
public final class Leash implements Ticking {

    private final Boss boss;
    private final double radius;

    public Leash(Boss boss, double radius) {
        this.boss = boss;
        this.radius = radius;
    }

    /**
     * How far, in blocks, a present boss may be from its recorded position before it is teleported back
     * (border-pregeneration.md § Movement). Safe baseline 40.
     */
    public static int standardRadius() {
        return new Tunable("bossLeashRadius", 8, 512, 40).intValue();
    }

    @Override
    public void onTickAfter(ScopeInfo info) {
        MobScope scope = info.scopeAs();
        Mob mob = scope.mob();
        // The leash teleports and retargets the boss, so it runs only on the server's side.
        if (Satchel.isServer()) {
            leash(mob.level(), mob);
        }
        // FRO_224: a scaled Keeper's path advances like a Sentinel's.
        WidePathing.advanceIfOnWaypoint(mob);
    }

    private void leash(Level level, Mob mob) {
        double radiusSq = radius * radius;
        BlockPos home = BossAPI.bosses(level).flatMap(fixture -> fixture.get(boss.bossId()))
                .map(BossRecord::position).orElse(null);
        // A home chunk that isn't loaded can't be teleported into; it is tried again next tick.
        if (home == null || !mob.isAlive() || !level.isLoaded(home)) {
            return;
        }
        double x = home.getX() + 0.5;
        double z = home.getZ() + 0.5;
        if (mob.distanceToSqr(x, home.getY(), z) <= radiusSq) {
            return;
        }
        OUT.info("[Boss] leash(): boss " + Ids.shortId(boss.bossId()) + " was "
                + (int) Math.sqrt(mob.distanceToSqr(x, home.getY(), z)) + " blocks from home (limit "
                + (int) radius + ") -- teleporting it back to " + home.getX() + ", " + home.getY() + ", " + home.getZ());
        puff(level, mob.getX(), mob.getY(), mob.getZ());
        mob.teleportTo(x, home.getY(), z);
        // It forgets the chase, so it doesn't walk straight back out; it picks a target up again as normal.
        mob.setTarget(null);
        mob.getNavigation().stop();
        puff(level, x, home.getY(), z);
    }

    private static void puff(Level level, double x, double y, double z) {
        EffectsAPI.broadcastParticle(level, ParticleTypes.PORTAL, x, y + 1.0, z, 30, 0.5, 1.0, 0.5, 0.1);
        EffectsAPI.broadcastSound(level, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
    }
}
