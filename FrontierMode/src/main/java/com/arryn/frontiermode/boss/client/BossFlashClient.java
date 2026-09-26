package com.arryn.frontiermode.boss.client;

import com.arryn.frontiermode.boss.BossMarkers;
import com.arryn.frontiermode.effects.client.ClientEffectsAPI;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The boss's close-range flash: a few motes of the shared amethyst dust at random points inside a
 * live boss's bounding box, drawn by each client that is close to it -- the same particle Cairn and
 * the guardian trail use, so a boss, its guardians and its Cairn read as one system up close. Client-
 * derived: "this entity is a boss" is already synced through {@code BossPublicFixture}, so nothing
 * is sent. Fire/no-fire rides {@link ClientEffectsAPI#spawnParticleOnParity}, keyed off the boss's
 * id and the world's game time. See boss.md's "Client-side visual marker" and effects.md.
 *
 * <p>Range and cadence are constants: {@code Tunable}s are server-only.
 *
 * <p>Client-only; the handler that calls it guards on the client side, so this class never loads on
 * a dedicated server.
 */
public final class BossFlashClient {

    /** A boss farther than this from the local player draws nothing. */
    private static final double RANGE = 24.0;
    /** The parity checkpoint interval, in ticks: one flash chance per boss this often. */
    private static final long CHECKPOINT_INTERVAL = 4L;
    /** Chance a checkpoint fires, per mote. */
    private static final double PROBABILITY = 0.5;
    /** Motes per checkpoint; each rolls independently (its own salt). */
    private static final int MOTES = 2;

    private BossFlashClient() {
    }

    public static void tick(Level level) {
        if (!(level instanceof ClientLevel clientLevel) || level.getGameTime() % CHECKPOINT_INTERVAL != 0) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.level() != level) {
            return;
        }

        double rangeSq = RANGE * RANGE;
        RandomSource random = level.getRandom();
        for (Mob boss : clientLevel.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE),
                BossMarkers::isLiveBoss)) {
            if (boss.distanceToSqr(player) > rangeSq) {
                continue;
            }
            AABB box = boss.getBoundingBox();
            for (int mote = 0; mote < MOTES; mote++) {
                ClientEffectsAPI.spawnParticleOnParity(
                        clientLevel, boss.getUUID(), mote, CHECKPOINT_INTERVAL, PROBABILITY,
                        EffectsAPI.AMETHYST_DUST,
                        box.minX + random.nextDouble() * box.getXsize(),
                        box.minY + random.nextDouble() * box.getYsize(),
                        box.minZ + random.nextDouble() * box.getZsize(),
                        0.0, 0.0, 0.0);
            }
        }
    }
}
