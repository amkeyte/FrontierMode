package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.feral.common.FeralLogic;
import com.arryn.frontiermode.feral.server.rules.FeralRules;
import com.arryn.frontiermode.spawn.MobSpawns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;

/**
 * The density companion (feral.md § The spawn decision): an extra hostile of the same type riding a real
 * {@code FinalizeSpawn}, its count scaled by frontier distance. Built and switched off, in favor of the
 * pack ({@link FeralPackSpawner}). A helper keeps no field; it takes the mob and the level as arguments.
 */
final class FeralDensity {

    // Switched off in favor of the pack's direct force-spawn near the player (FeralPackSpawner): the
    // density companion can only ride a spawn attempt vanilla chooses to make, which is too sparse to
    // guarantee an encounter. A plain flag and not a deletion, so it can be flipped back to compare.
    private static final boolean ENABLED = false;

    // Candidate spots MobSpawns.scatter tries for one density companion.
    private static final int SCATTER_TRIES = 4;

    private FeralDensity() {
    }

    /**
     * Density boost only -- {@link FeralTagging#onHostileJoining} is the actual tagging authority.
     * "Standby, don't crash": the spawning mob isn't a {@code MobCategory.MONSTER} hostile, or
     * {@code frontierDistance <= 0} (not in the Wilds), is a silent no-op. Never cancels the
     * spawn either way. Currently a no-op regardless, per {@link #ENABLED}.
     */
    static void rollOnSpawn(Mob mob, ServerLevel level) {
        if (!ENABLED) {
            return;
        }
        if (mob.getType().getCategory() != MobCategory.MONSTER) {
            return;
        }

        BlockPos spawnPos = mob.blockPosition();
        int frontierDistance = BorderAPI.frontierDistanceAt(level, spawnPos);
        if (frontierDistance <= 0) {
            return;
        }

        rollDensityCompanion(mob, level, spawnPos, frontierDistance);
    }

    /**
     * Density scales with raw distance and is uncapped: steadily more, not a flat multiplier that
     * flattens out. {@code MobSpawnEvent.FinalizeSpawn} fires per already-decided spawn attempt rather
     * than controlling how many attempts vanilla makes, so this is an approximation of a true
     * spawn-rate multiplier, the same limitation as Sick Wildlife's own density boost.
     *
     * <p>{@link FeralLogic#expectedDensityCount} returns a real-valued expectation, not a count --
     * turned into an actual integer the standard unbiased way: the whole part spawns
     * unconditionally, the fractional remainder is one more independent roll. E.g. an expectation
     * of 2.7 always spawns 2 companions and has a 70% chance of a 3rd, averaging 2.7 over many
     * events. Clamped to {@link FeralRules#densityMaxPerEvent()} -- a safety valve against an
     * extreme distance turning one spawn into a lag spike, not a design cap (see that method's
     * own doc).
     */
    private static void rollDensityCompanion(Mob mob, ServerLevel level, BlockPos spawnPos, int frontierDistance) {
        double expected = FeralLogic.expectedDensityCount(frontierDistance, FeralRules.densityCountScale());
        if (expected <= 0.0) {
            return;
        }

        int count = MobSpawns.rollCount(expected, FeralRules.densityMaxPerEvent(), level.getRandom());

        // Clamp to the room left under FeralRules.maxAliveFeral(): the density mechanic, not natural
        // spawns or tagging, is what can build a wall of mobs that eats the mob cap, so this is the one
        // place the cap is enforced. countAliveFeral() reads presence.
        int room = FeralRules.maxAliveFeral() - countAliveFeral(level);
        if (room <= 0) {
            return;
        }
        count = Math.min(count, room);

        for (int i = 0; i < count; i++) {
            spawnDensityCompanion(mob, level, spawnPos);
        }
    }

    /** How many Feral mobs the level holds now, from presence; 0 while presence isn't ready. */
    private static int countAliveFeral(ServerLevel level) {
        return FeralPresence.of(level).map(FeralPresence::count).orElse(0);
    }

    /**
     * Spawns one extra copy of {@code mob}'s own type near {@code nearPos}, with {@code VANILLA}
     * setup: vanilla's {@code Mob#finalizeSpawn} runs but the Forge event doesn't, so this never
     * re-enters {@link #rollOnSpawn} for the companion it creates.
     * {@link FeralTagging#onHostileJoining} still catches it on join and tags it, so no explicit tag call
     * is needed here.
     */
    private static void spawnDensityCompanion(Mob mob, ServerLevel level, BlockPos nearPos) {
        // Spread across ground the player has to maneuver through, not one dodge-able clump on the
        // original spawn -- see FeralRules.DENSITY_COMPANION_SPREAD_RADIUS.
        BlockPos spot = MobSpawns.scatter(
                level, mob, nearPos, FeralRules.DENSITY_COMPANION_SPREAD_RADIUS, 1.0F, SCATTER_TRIES);

        // The spread can cross back over a border edge, especially against a border that hasn't grown
        // large yet. Skip rather than place an extra, permanently-untagged hostile inside the
        // player's own safe zone (see the re-entry kill, FeralCleanup).
        if (BorderAPI.frontierDistanceAt(level, spot) <= 0) {
            return;
        }

        MobSpawns.spawn(level, mob.getType(), spot, MobSpawns.Setup.VANILLA);
    }
}
