package com.arryn.frontiermode.sentinel.client;

import com.arryn.frontiermode.effects.client.ClientEffectsAPI;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import com.arryn.frontiermode.sentinel.common.fixture.SentinelFixture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * What the client does for the Sentinels it knows (sentinels.md § The trail, § Size and adds), driven by the client
 * Trinket's tick: the trail, and the client hitbox.
 *
 * <p><b>The trail:</b> a short-lived line of the shared amethyst particle at the feet of every
 * sentinel near the local player, drawn by each client from state it already has. Whether a mob is a
 * sentinel comes from the synced {@link SentinelFixture}, and a tracked mob's position is
 * already known to the client, so no packet is needed. A moving sentinel leaves a line; one standing
 * still stacks particles on its spot, so it's marked either way. Each particle stays exactly where it
 * is dropped: the shared amethyst dust has no gravity and its speed is zeroed.
 *
 * <p>Short range and short lifetime are design constraints, not tuning accidents: a long-lived trail
 * would become a visible path to the boss. The three numbers are {@code Tunable}s on the server that
 * reach the client through the fixture.
 *
 * <p><b>The hitbox:</b> the synced scale lands after the entity exists, so each tick a Sentinel whose synced scale
 * differs from the one last applied has its dimensions refreshed, and the size handler reads the new scale. The last
 * scale applied is held in a weak map beside the trail state, keyed by the entity, so it ends with the entity.
 *
 * <p>Client-only; the Trinket that calls it guards on the client side, so this class never loads on
 * a dedicated server.
 */
public final class SentinelTrailClient {

    // The scale each client Sentinel's dimensions were last refreshed at.
    private static final Map<Entity, Float> APPLIED_SCALE = new WeakHashMap<>();

    private SentinelTrailClient() {
    }

    public static void tick(Level level, SentinelFixture fixture) {
        if (!(level instanceof ClientLevel clientLevel)) {
            return;
        }
        refreshDimensions(clientLevel, fixture);
        drawTrail(clientLevel, fixture);
    }

    private static void refreshDimensions(ClientLevel level, SentinelFixture fixture) {
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof Mob mob) || !fixture.isSentinel(mob.getUUID())) {
                continue;
            }
            float scale = fixture.scaleOf(mob.getUUID());
            Float applied = APPLIED_SCALE.get(mob);
            if (applied == null || applied != scale) {
                APPLIED_SCALE.put(mob, scale);
                mob.refreshDimensions();
            }
        }
    }

    private static void drawTrail(ClientLevel level, SentinelFixture fixture) {
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
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius),
                m -> fixture.isSentinel(m.getUUID()))) {
            if (mob.distanceToSqr(player) <= radiusSq) {
                ClientEffectsAPI.spawnParticleWithLifetime(
                        level, EffectsAPI.AMETHYST_DUST, mob.getX(), mob.getY(), mob.getZ(), lifetime);
            }
        }
    }
}
