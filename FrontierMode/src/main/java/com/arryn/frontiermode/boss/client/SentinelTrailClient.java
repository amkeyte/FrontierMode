package com.arryn.frontiermode.boss.client;

import com.arryn.frontiermode.boss.common.fixture.SentinelPublicFixture;
import com.arryn.frontiermode.effects.client.ClientEffectsAPI;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * The sentinel trail: a short-lived line of the shared amethyst particle at the feet of every
 * sentinel near the local player, drawn by each client from state it already has. Whether a mob is a
 * sentinel comes from the synced {@link SentinelPublicFixture}, and a tracked mob's position is
 * already known to the client, so no packet is needed. A moving sentinel leaves a line; one standing
 * still stacks particles on its spot, so it's marked either way. Each particle stays exactly where it
 * is dropped: the shared amethyst dust has no gravity and its speed is zeroed.
 *
 * <p>Short range and short lifetime are design constraints, not tuning accidents: a long-lived trail
 * would become a visible path to the boss. The three numbers are {@code Tunable}s on the server that
 * reach the client through the public fixture. See sentinels.md's "The trail".
 *
 * <p>Client-only; the handler that calls it guards on the client side, so this class never loads on
 * a dedicated server.
 */
public final class SentinelTrailClient {

    private SentinelTrailClient() {
    }

    public static void tick(Level level, SentinelPublicFixture fixture) {
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
                m -> fixture.isSentinel(m.getUUID()))) {
            if (mob.distanceToSqr(player) <= radiusSq) {
                ClientEffectsAPI.spawnParticleWithLifetime(
                        clientLevel, EffectsAPI.AMETHYST_DUST, mob.getX(), mob.getY(), mob.getZ(), lifetime);
            }
        }
    }
}
