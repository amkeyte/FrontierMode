package com.arryn.frontiermode.boss.client;

import com.arryn.frontiermode.boss.common.fixture.GuardianPublicFixture;
import com.arryn.frontiermode.effects.client.ClientEffectsAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * The guardian trail: a short-lived line of the shared amethyst particle at the feet of every
 * guardian near the local player, drawn by each client from state it already has. Whether a mob is a
 * guardian comes from the synced {@link GuardianPublicFixture}, and a tracked mob's position is
 * already known to the client, so no packet is needed. A moving guardian leaves a line; one standing
 * still stacks particles on its spot, so it's marked either way.
 *
 * <p>Short range and short lifetime are design constraints, not tuning accidents: a long-lived trail
 * would become a visible path to the boss. The three numbers are {@code Tunable}s on the server that
 * reach the client through the public fixture. See guardian-mobs.md's "The trail".
 *
 * <p>Client-only; the handler that calls it guards on the client side, so this class never loads on
 * a dedicated server.
 */
public final class GuardianTrailClient {

    // The shared amethyst particle isn't registered in code yet; Cairn's ambient signal uses the same
    // stand-in. Swap for the shared particle when it lands.
    private static final net.minecraft.core.particles.ParticleOptions PARTICLE = ParticleTypes.WITCH;

    private GuardianTrailClient() {
    }

    public static void tick(Level level, GuardianPublicFixture fixture) {
        if (!(level instanceof ClientLevel clientLevel)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.level() != level) {
            return;
        }
        int interval = Math.max(1, fixture.trailIntervalTicks());
        if (level.getGameTime() % interval != 0) {
            return;
        }

        int radius = fixture.trailRadius();
        int lifetime = fixture.trailLifetimeTicks();
        double radiusSq = (double) radius * radius;
        for (Mob mob : clientLevel.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius),
                m -> fixture.isGuardian(m.getUUID()))) {
            if (mob.distanceToSqr(player) <= radiusSq) {
                ClientEffectsAPI.spawnParticleWithLifetime(
                        clientLevel, PARTICLE, mob.getX(), mob.getY(), mob.getZ(), lifetime);
            }
        }
    }
}
