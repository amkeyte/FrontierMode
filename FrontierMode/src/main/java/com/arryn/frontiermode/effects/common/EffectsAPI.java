package com.arryn.frontiermode.effects.common;

import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Vector3f;

/**
 * Side-agnostic ingress facade for cross-cutting effect dispatch (particles, sounds) -- mirrors
 * {@code BorderAPI}/{@code BossAPI}'s shape. See wiki/frontiermode/architecture/effects.md and
 * FRO_089.
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
     * (FRO_087/FRO_089). Requires a real {@link ServerPlayer} (not just a {@code Player}) because
     * the underlying targeted-send overload only exists on {@code ServerLevel}, resolved here via
     * {@link ServerPlayer#serverLevel()} so callers don't need to carry a separate
     * {@code ServerLevel} reference alongside the player. {@code forceVisible=false} lets
     * vanilla's own render-distance rules decide whether the client actually shows it.
     */
    @SuppressWarnings("resource")
    public static void sendParticleToPlayer(
            ServerPlayer player,
            ParticleOptions particle,
            double x, double y, double z,
            int count,
            double xOffset, double yOffset, double zOffset,
            double speed
    ) {
        //noinspection resource
        player.serverLevel().sendParticles(
                player, particle, false, x, y, z, count, xOffset, yOffset, zOffset, speed);
    }

    /**
     * FRO_120 (Cairn): area particle broadcast, the particle-side counterpart to
     * {@link #broadcastSound} below -- every nearby tracking player gets it, per
     * {@code ServerLevel.sendParticles}'s own no-target overload, rather than
     * {@link #sendParticleToPlayer}'s single-viewer shape. First real consumer: Cairn's own pulse
     * bee-line, which several players standing near the same Cairn should all see at once.
     */
    public static void broadcastParticle(
            ServerLevel level,
            ParticleOptions particle,
            double x, double y, double z,
            int count,
            double xOffset, double yOffset, double zOffset,
            double speed
    ) {
        level.sendParticles(particle, x, y, z, count, xOffset, yOffset, zOffset, speed);
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
     * RM_FRO_037 ("Brenda," Frontier Sickness epoch 1): targeted sound send, visible only to
     * {@code player} -- the sound-side counterpart to {@link #sendParticleToPlayer} above, added
     * for Entry Cue (a per-player-only tell, not an area broadcast). {@code Level.playSound}'s
     * {@code Player} parameter is deliberately not a target -- it's the player to *exclude* from
     * an otherwise-broadcast sound (vanilla's own "don't double-play this player's own sound
     * effect back at them" case) -- so it can't do this. Sending a {@link ClientboundSoundPacket}
     * straight to {@code player}'s own connection is the standard vanilla mechanism for a sound
     * only one specific player hears.
     */
    public static void sendSoundToPlayer(
            ServerPlayer player,
            SoundEvent sound,
            SoundSource category,
            double x, double y, double z,
            float volume,
            float pitch
    ) {
        player.connection.send(new ClientboundSoundPacket(
                Holder.direct(sound), category, x, y, z, volume, pitch, player.getRandom().nextLong()));
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

    /**
     * Idempotent create-if-absent for a named {@link PlayerTeam} -- proposed shape from
     * wiki/frontiermode/architecture/effects.md#team-assignment-persistent-visual-state, mirroring
     * this codebase's own {@code createTellCurveIfAbsent}/{@code ensureSentinelCurves}
     * convention. Team membership is persistent, server-authoritative entity state (unlike a
     * particle burst or a sound), so it has no client-derived/parity counterpart -- every real
     * caller is this server-broadcast path.

     */
    public static PlayerTeam ensureTeam(Scoreboard scoreboard, String name, ChatFormatting color) {
        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) {
            team = scoreboard.addPlayerTeam(name);
            team.setColor(color);
        }
        return team;
    }

    /**
     * Adds {@code entity} to {@code team} via {@code scoreboard}. Takes the {@link Scoreboard}
     * explicitly rather than reading it off {@code entity} -- {@code Entity} has no
     * {@code getScoreboard()} accessor in the real API (the wiki page's own sketch assumed one;
     * checked against real Forge/Mojang-mapped 1.20.1 sources, it doesn't exist -- callers already
     * have a {@code ServerLevel}/{@code Scoreboard} in hand at every real call site, e.g. via
     * {@code level.getScoreboard()}, same as {@link #ensureTeam} above).
     */
    public static void assignToTeam(Entity entity, Scoreboard scoreboard, PlayerTeam team) {
        scoreboard.addPlayerToTeam(entity.getScoreboardName(), team);
    }
}
