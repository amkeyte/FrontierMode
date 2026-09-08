package com.arryn.frontiermode.effects.client;

import com.arryn.frontiermode.effects.common.EffectsAPI;
import com.arryn.satchel.common.util.SimParity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.UUID;

/**
 * Client-derived (parity) counterpart to {@link EffectsAPI}'s server-broadcast methods -- every
 * client that calls these computes the same fire/no-fire answer independently, off state it
 * already has synced (a stable id and the world's own {@code GameTime}), with no packet involved.
 * Valid only when an effect's occurrence can be expressed as a pure function of already-synced
 * inputs -- see wiki/frontiermode/architecture/effects.md#two-dispatch-paths-chosen-per-effect
 * and FRO_089. Built on {@link SimParity} (SAT_047): {@link SimParity#isCheckpoint} gates which
 * ticks are even eligible, {@link SimParity#parityRoll} decides fire/no-fire on those ticks --
 * both sides reading the same shared {@code GameTime} counter is what makes two independent
 * clients (or a client and the server, if the server ever wants to run the identical check for
 * its own reasons) agree by construction rather than by broadcast.
 *
 * <p>Not wired to a real consumer yet as of FRO_089's initial build -- {@code
 * GrowthTriggerRenderer}'s particle is the likely first one, once FRO_055's stale-tip bug clears
 * (see FRO_089's own migration notes for why that one isn't moved yet). Built for real now,
 * rather than left as a stub, so the shape doesn't need to be re-derived later.
 *
 * <p>A sound or particle gated on server-only state (attunement, team membership, anything a
 * client isn't told) can't be derived client-side at all -- that case always belongs on
 * {@link EffectsAPI}'s server-broadcast methods instead, never here.
 */
public final class ClientEffectsAPI {

    private ClientEffectsAPI() {
    }

    /**
     * Fires a single client-local particle if, and only if, {@code level.getGameTime()} is a
     * parity checkpoint on {@code checkpointInterval} and this client's own parity roll for
     * {@code sourceId}/{@code salt} clears {@code probability}. {@code sourceId} must be a stable
     * id already synced to every client watching it (a border id, a boss id, ...) -- every
     * client resolves the identical roll on the identical tick from that id alone, so nothing
     * needs to be sent over the network to keep them in agreement.
     *
     * @param salt per-call-site discriminator (see {@link SimParity#parityValue}) so two
     *             unrelated effects sharing an id/tick don't accidentally correlate
     */
    public static void spawnParticleOnParity(
            ClientLevel level,
            UUID sourceId,
            long salt,
            long checkpointInterval,
            double probability,
            ParticleOptions particle,
            double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed
    ) {
        long gameTime = level.getGameTime();
        if (!SimParity.isCheckpoint(gameTime, checkpointInterval)) {
            return;
        }
        if (SimParity.parityRoll(sourceId, gameTime, salt) >= probability) {
            return;
        }
        level.addParticle(particle, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    /**
     * Client-local sound, same parity-gate shape as {@link #spawnParticleOnParity} -- no
     * broadcast packet, every client decides independently via {@code Level.playLocalSound},
     * which only actually plays anything on a real client {@link ClientLevel}.
     */
    public static void playSoundOnParity(
            ClientLevel level,
            UUID sourceId,
            long salt,
            long checkpointInterval,
            double probability,
            SoundEvent sound,
            SoundSource category,
            double x, double y, double z,
            float volume, float pitch
    ) {
        long gameTime = level.getGameTime();
        if (!SimParity.isCheckpoint(gameTime, checkpointInterval)) {
            return;
        }
        if (SimParity.parityRoll(sourceId, gameTime, salt) >= probability) {
            return;
        }
        level.playLocalSound(x, y, z, sound, category, volume, pitch, false);
    }
}
