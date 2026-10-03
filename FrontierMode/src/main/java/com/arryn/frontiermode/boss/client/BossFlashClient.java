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
 * The boss's close-range flash: a few motes of the shared amethyst dust hovering just outside a live
 * boss's surface, drawn by each client that is close to it -- the same particle Cairn and the
 * sentinel trail use, so a boss, its sentinels and its Cairn read as one system up close. Client-
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

    // How far outside the body's surface a mote hovers.
    private static final double OFFSET = 0.25;

    private BossFlashClient() {
    }

    /**
     * A random point hovering just outside one of the box's sides or its top -- not inside it: a
     * boss's solid model would hide a mote spawned within its body from most angles, and the flash
     * would never be seen. Never below the feet, where the ground would hide it.
     */
    private static double[] pointOutside(AABB box, RandomSource random) {
        double x = box.minX + random.nextDouble() * box.getXsize();
        double y = box.minY + random.nextDouble() * box.getYsize();
        double z = box.minZ + random.nextDouble() * box.getZsize();
        switch (random.nextInt(5)) {
            case 0 -> x = box.minX - OFFSET;
            case 1 -> x = box.maxX + OFFSET;
            case 2 -> z = box.minZ - OFFSET;
            case 3 -> z = box.maxZ + OFFSET;
            default -> y = box.maxY + OFFSET;
        }
        return new double[]{x, y, z};
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
                double[] p = pointOutside(box, random);
                ClientEffectsAPI.spawnParticleOnParity(
                        clientLevel, boss.getUUID(), mote, CHECKPOINT_INTERVAL, PROBABILITY,
                        EffectsAPI.AMETHYST_DUST, p[0], p[1], p[2], 0.0, 0.0, 0.0);
            }
        }
    }
}
