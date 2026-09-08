package com.arryn.frontiermode.effects.common;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

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
    public static void sendParticleToPlayer(
            ServerPlayer player,
            ParticleOptions particle,
            double x, double y, double z,
            int count,
            double xOffset, double yOffset, double zOffset,
            double speed
    ) {
        player.serverLevel().sendParticles(
                player, particle, false, x, y, z, count, xOffset, yOffset, zOffset, speed);
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
     * Idempotent create-if-absent for a named {@link PlayerTeam} -- proposed shape from
     * wiki/frontiermode/architecture/effects.md#team-assignment-persistent-visual-state, mirroring
     * this codebase's own {@code createTellCurveIfAbsent}/{@code ensureGuardianCurves}
     * convention. Team membership is persistent, server-authoritative entity state (unlike a
     * particle burst or a sound), so it has no client-derived/parity counterpart -- every real
     * caller is this server-broadcast path.
     *
     * <p>First real consumer: Guardian Mobs (RM_FRO_029) -- a single shared {@code "guardian"}
     * team, colored {@link ChatFormatting#DARK_PURPLE}, applied regardless of tier. Known
     * limitation, accepted 2026-09-07 (project owner's direct call): team membership is allowed
     * to leak -- nothing here removes an entry when the underlying entity dies or despawns, since
     * Guardian Mobs keeps no record of which mobs are guardians to clean one up from. See the wiki
     * section above for the full acceptance.
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
