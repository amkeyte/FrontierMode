package com.arryn.frontiermode.effects.common;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

/**
 * Side-agnostic ingress facade for cross-cutting effect dispatch (particles, sounds) -- mirrors
 * {@code BorderAPI}/{@code BossAPI}'s shape. See wiki/frontiermode/architecture/effects.md.
 *
 * <p><b>One code set on both sides.</b> Each method takes the common type ({@code Level}, {@code Player}) and
 * does its server work only when it is handed the server's: given a client's level or player it does nothing,
 * because the server sends the effect. The side difference lives in this facade, which is the I/O, so a caller
 * carries no {@code ServerLevel} or {@code ServerPlayer}.
 *
 * <p><b>Server-broadcast path:</b> the methods on this class. The server makes a real decision
 * (a probability roll, anything depending on server-only state) once and pushes the result to
 * players -- required whenever the occurrence itself is non-deterministic or depends on
 * something not synced to every client. See
 * {@link com.arryn.frontiermode.effects.client.ClientEffectsAPI} in {@code effects.client} for
 * the other dispatch path (client-derived/parity): every client independently computes the same
 * fire/no-fire answer off already-synced state, with no packet involved.
 *
 * <p>{@code EffectsMod} never reaches back into a calling module's own data model to decide what
 * to draw or play -- callers pass plain parameters (a position, a particle type, a sound event);
 * this class dispatches, it doesn't own anyone's domain data.
 */
public final class EffectsAPI {

    private EffectsAPI() {
    }

    /**
     * Targeted particle send, visible only to {@code player} -- the shape
     * {@code BossTellFixture}'s own tell particle already used before this module existed
     *. The targeted-send overload only exists on {@code ServerLevel} and takes a
     * {@code ServerPlayer}, so this sends only when {@code player} is one, resolving the level through
     * {@link ServerPlayer#serverLevel()}; for any other {@code Player} it does nothing.
     * {@code forceVisible=false} lets vanilla's own render-distance rules decide whether the client
     * actually shows it.
     */
    @SuppressWarnings("resource")
    public static void sendParticleToPlayer(
            Player player,
            ParticleOptions particle,
            double x, double y, double z,
            int count,
            double xOffset, double yOffset, double zOffset,
            double speed
    ) {
        if (player instanceof ServerPlayer serverPlayer) {
            //noinspection resource
            serverPlayer.serverLevel().sendParticles(
                    serverPlayer, particle, false, x, y, z, count, xOffset, yOffset, zOffset, speed);
        }
    }

    /**
     * area particle broadcast, the particle-side counterpart to
     * {@link #broadcastSound} below -- every nearby tracking player gets it, per
     * {@code ServerLevel.sendParticles}'s own no-target overload, rather than
     * {@link #sendParticleToPlayer}'s single-viewer shape. First real consumer: Cairn's own pulse
     * bee-line, which several players standing near the same Cairn should all see at once. Sends only when
     * {@code level} is the server's; for a client's level it does nothing.
     */
    public static void broadcastParticle(
            Level level,
            ParticleOptions particle,
            double x, double y, double z,
            int count,
            double xOffset, double yOffset, double zOffset,
            double speed
    ) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(particle, x, y, z, count, xOffset, yOffset, zOffset, speed);
        }
    }

    /**
     * Area sound broadcast -- every player within vanilla's own hearing range gets it, per
     * {@code Level.playSound(null, ...)}'s own broadcast semantics. {@code level} may be either
     * side's {@link Level} in principle, but every real caller today holds a {@code ServerLevel}
     * already (this is the server-broadcast path) -- pass that directly, no cast needed.
     */
    public static void broadcastSound(
            Level level,
            double x, double y, double z,
            SoundEvent sound,
            SoundSource category,
            float volume,
            float pitch
    ) {
        level.playSound(null, x, y, z, sound, category, volume, pitch);
    }

    /**
     * targeted sound send, visible only to
     * {@code player} -- the sound-side counterpart to {@link #sendParticleToPlayer} above, added
     * for Entry Cue (a per-player-only tell, not an area broadcast). {@code Level.playSound}'s
     * {@code Player} parameter is deliberately not a target -- it's the player to *exclude* from
     * an otherwise-broadcast sound (vanilla's own "don't double-play this player's own sound
     * effect back at them" case) -- so it can't do this. Sending a {@link ClientboundSoundPacket}
     * straight to {@code player}'s own connection is the standard vanilla mechanism for a sound
     * only one specific player hears. The connection is a {@code ServerPlayer}'s, so this sends only when
     * {@code player} is one; for any other {@code Player} it does nothing.
     */
    public static void sendSoundToPlayer(
            Player player,
            SoundEvent sound,
            SoundSource category,
            double x, double y, double z,
            float volume,
            float pitch
    ) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSoundPacket(
                    Holder.direct(sound), category, x, y, z, volume, pitch, serverPlayer.getRandom().nextLong()));
        }
    }

    /**
     * The shared amethyst particle until a custom type is registered: vanilla dust in an amethyst
     * purple, so every consumer (a sentinel's trail, Cairn, the boss's flash) draws the same colour.
     * Dust has no gravity, so a consumer that wants it to stay put can zero its speed.
     */
    public static final DustParticleOptions AMETHYST_DUST =
            new DustParticleOptions(new Vector3f(0.65F, 0.45F, 0.9F), 1.0F);

    /**
     * A new boss bar with no viewers -- persistent visual state, like a team, so server-authoritative
     * and with no client-derived counterpart. Callers add and remove players and set progress on the
     * returned handle, and must {@link BossBarHandle#removeAll()} when they're done with it: a bar
     * nobody removes stays on its viewers' screens. See effects.md § Boss bars.
     */
    public static BossBarHandle createBossBar(
            Component title, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
        return new BossBarHandle(new ServerBossEvent(title, color, overlay));
    }
}
