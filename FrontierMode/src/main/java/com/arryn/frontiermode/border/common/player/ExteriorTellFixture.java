package com.arryn.frontiermode.border.common.player;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BordersCrudFacet;
import com.arryn.frontiermode.border.server.rules.PlayerRules;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * RM_FRO_037 ("Brenda," Frontier Sickness epoch 1 / FRO_099): Sick Wildlife's cosmetic tell --
 * poison-colored particles and periodic hurt sounds on already-present passive mobs found in the
 * Exterior, per wiki/frontiermode/design/exterior.md#sensory-design's "Sick wildlife" bullet.
 * Cosmetic only: nothing here damages or despawns a mob (per that page, they're only "defeated"
 * by the player carrying them back across the Frontier themselves, which needs no code here at
 * all -- a mob that walks back inside the boundary simply stops registering a positive
 * frontierDistance of its own and this fixture leaves it alone).
 *
 * <p>Gates per ANIMAL, not per player: each nearby {@link Animal}'s own position is checked
 * against every {@code Border} (same {@code BorderAPI.MATH.distanceOutside} reduction
 * {@code BorderModule.onMobSpawnFinalize} and {@code BorderPlayerLogic} already use) -- an
 * earlier version of this fixture gated the whole pass on the PLAYER's own
 * {@code BorderPlayerStatusFixture} reading instead, which was wrong: a player standing just
 * inside the boundary could stand right next to animals well out in the Exterior and never see
 * a single tell, and conversely a player deep outside would roll effects on animals that were
 * themselves still technically inside. Confirmed by playtest (2026-09-08) once the underlying
 * SAT_049 dispatch bug was fixed -- see BorderModule.onPlayerScopeTick's own doc for that
 * history. No dependency on {@link BorderPlayerBundle}/{@link BorderPlayerStatusFixture} at all
 * anymore -- only the player's position (to center the search) and each animal's own position.
 *
 * <p>Sibling fixture to {@link BorderPlayerStatusFixture} inside {@link BorderPlayerBundle}
 * (shares the bundle for lifecycle/dispatch convenience only, not for its data), mirroring
 * {@code BossTellFixture}'s own cadence-gated tick shape and its two-independent-rolls dispatch
 * (one for particle, one for sound) -- the closest real precedent in this codebase for
 * "periodically roll a cosmetic effect near a player." Player-scoped rather than
 * {@code BossTellFixture}'s level-scoped (one instance per boss), since Sick Wildlife has no
 * Boss/border-owner to key off -- see
 * wiki/frontiermode/architecture/exterior.md#no-boss-dependency-at-all-the-cleanest-dependency-shape-of-anything-in-this-cluster.
 * Zero persisted state -- no {@code registerCustom(...)} call -- for the same reason
 * {@code BossGuardiansFixture} has none: nothing here needs to survive a restart.
 *
 * <p>Density scaling (mobs spawning more densely with distance, per FRO_098) is a separate
 * concern handled at spawn time by {@code BorderModule.onMobSpawnFinalize}, not here -- this
 * fixture only ever affects mobs that already exist.
 */
public final class ExteriorTellFixture extends SatchelFixture {

    // Shared RNG -- same static-field, not-persisted shape BossTellFixture.RNG/
    // BossGuardiansFixture.RNG already use.
    private static final RandomSource RNG = RandomSource.create();

    // Non-persisted tell-pass cadence counter, per-player (this fixture instance is itself
    // player-scoped). Resets on server restart -- "off/on signal" discipline, no harm done, same
    // as BossTellFixture's own tickCounter.
    private int tickCounter = 0;

    @Override
    public void onJigTick() {
        Satchel.requireServer();

        tickCounter++;
        if (tickCounter < PlayerRules.WILDLIFE_TELL_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        PlayerScope playerScope = (PlayerScope) scope();
        ServerPlayer player = playerScope.player();
        ServerLevel level = player.serverLevel();

        // RM_FRO_037 fix (2026-09-08, per project owner playtest): gate PER ANIMAL against its
        // own position, not the player's -- see this class's own doc for why the player-gated
        // version was wrong. Same distanceOutside-over-borders reduction
        // BorderModule.onMobSpawnFinalize already uses for the density hook.
        List<Border> borders = BorderAPI.CRUD(level).map(BordersCrudFacet::all).orElseGet(List::of);
        if (borders.isEmpty()) {
            return;
        }

        AABB searchArea = new AABB(player.blockPosition()).inflate(PlayerRules.WILDLIFE_TELL_RADIUS);
        List<Animal> nearby = level.getEntitiesOfClass(Animal.class, searchArea);

        int sickCount = 0;

        for (Animal animal : nearby) {
            BlockPos animalPos = animal.blockPosition();
            int frontierDistance = borders.stream()
                    .mapToInt(b -> BorderAPI.MATH.distanceOutside(animalPos, b.center(), b.radius()))
                    .min()
                    .orElse(0);
            if (frontierDistance <= 0) {
                // This particular animal isn't in the Exterior -- not sick, even if the player
                // standing near it is.
                continue;
            }
            sickCount++;

            Vec3 pos = animal.position();

            // Two independent rolls, same shape BossTellFixture.runTellsForPlayers uses --
            // particle is targeted (only this player sees their own local tell), sound is an
            // area broadcast (anyone nearby hears the wildlife, not just this one player).
            if (RNG.nextDouble() < PlayerRules.WILDLIFE_TELL_PARTICLE_CHANCE) {
                // Placeholder particle -- WITCH reads as "something magical/wrong here" and needs
                // no ColorParticleOption plumbing to compile; swap for a real poison-green
                // particle once confirmed against actual 1.20.1 sources (Lead Dev's call, same
                // hedge every other unverified API touch point in this cluster carries).
                EffectsAPI.sendParticleToPlayer(
                        player, ParticleTypes.WITCH,
                        pos.x, pos.y + animal.getBbHeight() * 0.5, pos.z,
                        6, 0.2, 0.2, 0.2, 0.01);
            }
            if (RNG.nextDouble() < PlayerRules.WILDLIFE_TELL_SOUND_CHANCE) {
                // Placeholder sound -- generic hurt reads as "ouch" without a dedicated cue;
                // real tuning is playtest territory, same as every other placeholder here.
                EffectsAPI.broadcastSound(
                        level, pos.x, pos.y, pos.z,
                        SoundEvents.GENERIC_HURT, SoundSource.AMBIENT,
                        0.4f, 1.4f + RNG.nextFloat() * 0.3f);
            }
        }

        // TEMP DIAGNOSTIC (2026-09-08, RM_FRO_037): now reports the per-animal split -- how many
        // were nearby vs. how many actually counted as "in the Exterior" and got rolled against.
        // Safe to delete once confirmed good in play.
        OUT.info("[ExteriorTell] player=" + player.getGameProfile().getName()
                + " nearbyAnimals=" + nearby.size() + " sickEligible=" + sickCount);
    }
}
