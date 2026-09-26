package com.arryn.frontiermode.structure.client;

import com.arryn.frontiermode.effects.common.EffectsAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * The Cairn's ambient signal: a few motes of the shared amethyst dust around the cap, drawn by the
 * client for any cap it has loaded near the local player. Client-derived from the cap's own block
 * position -- vanilla calls {@code Block#animateTick} on the client for blocks near the player, at a
 * random rate, so no fixture, packet, or event is involved -- and hard range-limited so a Cairn is
 * never a landmark visible across the map. See cairn.md's "The ambient signal".
 *
 * <p>Range and density are constants: {@code Tunable}s are server-only.
 *
 * <p>Client-only; {@code CairnCapBlock} calls it only on the client side, so this class never loads
 * on a dedicated server.
 */
public final class CairnAmbientClient {

    /** A cap farther than this from the local player draws nothing. */
    private static final double RANGE = 24.0;
    /** Motes per {@code animateTick} call on the cap (about 0.4 calls a second nearby). */
    private static final int MOTES = 3;
    /** How far from the cap's top centre a mote appears, horizontally. */
    private static final double SPREAD = 0.4;
    /** How far above the cap's top a mote can appear. */
    private static final double RISE = 0.5;

    private CairnAmbientClient() {
    }

    public static void emit(Level level, BlockPos capPos, RandomSource random) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        double x = capPos.getX() + 0.5;
        double z = capPos.getZ() + 0.5;
        if (player.distanceToSqr(x, capPos.getY() + 0.5, z) > RANGE * RANGE) {
            return;
        }
        for (int i = 0; i < MOTES; i++) {
            level.addParticle(EffectsAPI.AMETHYST_DUST,
                    x + (random.nextDouble() * 2.0 - 1.0) * SPREAD,
                    capPos.getY() + 1.0 + random.nextDouble() * RISE,
                    z + (random.nextDouble() * 2.0 - 1.0) * SPREAD,
                    0.0, 0.0, 0.0);
        }
    }
}
