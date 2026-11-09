package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import com.arryn.frontiermode.feral.server.rules.FeralRules;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * Feral's work for one player each tick (feral.md § How Feral hooks the game): the pack top-up and the
 * cosmetic tell. A Trinket contributed to the player host on the server, running after
 * {@link FeralPresence} (order {@code NORMAL}, presence {@code EARLY}), so it reads this tick's presence.
 * Feral keeps no jig, so nothing here is declared on one and nothing is saved.
 *
 * <p>Both pieces are gated on {@code player.tickCount} with the modulo shape {@code
 * FrontierSickness.applySicknessEffects} uses, rather than a per-player counter field. The player's scope
 * outlives death, respawn and a dimension change and looks its player up fresh on every call, so one
 * instance serves a player for the whole login; its random source is the one field it keeps.
 */
public final class FeralPlayerTick implements Ticking {

    private final RandomSource random = RandomSource.create();

    @Override
    public void onAttach(ScopeInfo info) {
        OUT.debug("[Feral] FeralPlayerTick attached to " + info.debugName());
    }

    @Override
    public void onTickAfter(ScopeInfo info) {
        PlayerScope scope = info.scopeAs();
        ServerPlayer player = (ServerPlayer) scope.player();

        maintainPack(player);

        if (player.tickCount % FeralRules.tellIntervalTicks() != 0) {
            return;
        }

        ServerLevel level = player.serverLevel();
        Optional<FeralPresence> presence = FeralPresence.of(level);
        if (presence.isEmpty()) {
            return;
        }
        AABB searchArea = new AABB(player.blockPosition()).inflate(FeralRules.tellRadius());
        for (Mob mob : presence.get().mobs()) {
            if (searchArea.intersects(mob.getBoundingBox())) {
                rollTell(player, level, mob);
            }
        }
    }

    @Override
    public String describe() {
        return "Feral player tick: the pack top-up and the cosmetic tell";
    }

    /**
     * The cosmetic tell on an already-spawned Feral mob -- poison particles and hurt sounds, mirroring
     * {@code WildsTellFixture}'s Sick Wildlife tell (two independent rolls, particle targeted and sound
     * broadcast). Never touches health or combat.
     */
    private void rollTell(Player player, Level level, Mob monster) {
        Vec3 pos = monster.position();

        // Two independent rolls, same shape WildsTellFixture/BossTellFixture already use --
        // particle is targeted (only this player sees their own local tell), sound is an area
        // broadcast (anyone nearby hears it, not just this one player).
        if (random.nextDouble() < FeralRules.tellParticleChance()) {
            // Placeholder particle -- same WITCH placeholder WildsTellFixture uses, same
            // "swap for a real poison-green particle once confirmed against real sources" hedge.
            EffectsAPI.sendParticleToPlayer(
                    player, ParticleTypes.WITCH,
                    pos.x, pos.y + monster.getBbHeight() * 0.5, pos.z,
                    6, 0.2, 0.2, 0.2, 0.01);
        }
        if (random.nextDouble() < FeralRules.tellSoundChance()) {
            // Placeholder sound -- same GENERIC_HURT placeholder WildsTellFixture uses.
            EffectsAPI.broadcastSound(
                    level, pos.x, pos.y, pos.z,
                    SoundEvents.GENERIC_HURT, SoundSource.HOSTILE,
                    0.4f, 1.4f + random.nextFloat() * 0.3f);
        }
    }

    /**
     * While a player is in the Wilds, tops up the pack near them: counts the Feral mobs in presence within
     * {@link FeralRules#packMaintainRadius()} that are ahead of the player
     * ({@link FeralPackSpawner#countAhead}), and if there are fewer than {@link FeralRules#packTargetCount()},
     * force-spawns the shortfall with {@link FeralPackSpawner#spawnMember}, clamped to the room under
     * {@link FeralRules#maxAlivePackMembers()}. The budget counts pack members only, so ambient spawns can
     * never crowd the pack out. A no-op outside the Wilds. Runs on its own cadence
     * ({@link FeralRules#packMaintainIntervalTicks()}), independent of the tell's.
     */
    private void maintainPack(ServerPlayer player) {
        if (player.tickCount % FeralRules.packMaintainIntervalTicks() != 0) {
            return;
        }

        // warn (never clamp) if the pack Tunables' documented relationships are broken.
        FeralRules.warnIfPackTunablesInconsistent();

        ServerLevel level = player.serverLevel();
        if (BorderAPI.frontierDistanceAt(level, player.blockPosition()) <= 0) {
            return;
        }

        Optional<FeralPresence> presence = FeralPresence.of(level);
        if (presence.isEmpty()) {
            return;
        }

        AABB maintainArea = new AABB(player.blockPosition()).inflate(FeralRules.packMaintainRadius());
        int ahead = FeralPackSpawner.countAhead(
                presence.get().mobs(), maintainArea, player.position(), player.getYRot());
        if (ahead >= FeralRules.packTargetCount()) {
            return;
        }

        int room = FeralRules.maxAlivePackMembers() - presence.get().packCount();
        if (room <= 0) {
            return;
        }

        int toSpawn = Math.min(FeralRules.packTargetCount() - ahead, room);
        for (int i = 0; i < toSpawn; i++) {
            FeralPackSpawner.spawnMember(player, level, random);
        }
    }
}
