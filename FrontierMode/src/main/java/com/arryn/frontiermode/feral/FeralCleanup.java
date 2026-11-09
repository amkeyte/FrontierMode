package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.feral.server.rules.FeralRules;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.common.util.schedule.TickDelay;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What {@link FeralPresence} does to the Feral mobs it holds each sweep (feral.md § Re-entry kill, § The
 * pack): the re-entry kill, the despawn rules, the linger rule that is built and switched off, the
 * split-child cleanup, and the pack diagnostic log. A helper keeps no field; it takes the level and
 * presence as arguments.
 */
final class FeralCleanup {

    // Built and switched off: the lingering despawn timer, kept to flip back on and compare.
    private static final boolean LEGACY_DISTANCE_DESPAWN_ENABLED = false;

    // Built and switched off: the once-a-second pack diagnostic log.
    private static final boolean DIAGNOSTIC_LOGGING_ENABLED = false;

    private FeralCleanup() {
    }

    /**
     * One pass over presence, two independent cleanup checks per mob:
     *
     * <ol>
     *   <li><b>Re-entered the Frontier:</b> it is killed, not silently discarded -- a real death, so it
     *       reads as "the Frontier killed it". It drops nothing: a Feral mob never does
     *       (the drops and XP handlers cancel them). A Slime's split children are discarded.
     *   <li><b>Still in the Wilds, but too far from every player:</b> a pack member goes by
     *       {@link #despawnIfBeyondPackRadius}, an ambient mob by {@link #despawnIfBeyondAmbientRadius}.
     * </ol>
     *
     * <p>Level-wide, not bounded to a player's tell radius: the guarantee has to hold everywhere a Feral
     * mob might wander back to.
     */
    static void sweep(Level level, FeralPresence presence) {
        // Collect first, act after: killing or discarding a mob removes entities (a slime's split
        // children), and presence drops them on its own tick, not mid-sweep.
        List<Mob> feral = new ArrayList<>(presence.mobs());

        for (Mob monster : feral) {
            // An earlier iteration (a slime's split cleanup) or vanilla may already have removed it.
            if (monster.isRemoved()) {
                continue;
            }

            if (BorderAPI.frontierDistanceAt(level, monster.blockPosition()) <= 0) {
                killAndCleanupSplit(level, monster);
                presence.despawnTimers().remove(monster.getUUID());
                continue;
            }

            if (LEGACY_DISTANCE_DESPAWN_ENABLED) {
                despawnWithLegacyLinger(level, monster, presence.despawnTimers());
            } else if (presence.isPack(monster.getUUID())) {
                despawnIfBeyondPackRadius(level, monster);
            } else {
                despawnIfBeyondAmbientRadius(level, monster);
            }
        }
    }

    /**
     * A lingering {@link TickDelay} per mob, tolerating a brief crossing of
     * {@link FeralRules#despawnRadius()} without flickering. Switched off
     * ({@link #LEGACY_DISTANCE_DESPAWN_ENABLED}); kept so it is a one-flag flip to compare against
     * {@link #despawnIfBeyondPackRadius}.
     */
    private static void despawnWithLegacyLinger(Level level, Mob monster, Map<UUID, TickDelay> timers) {
        Player nearest = level.getNearestPlayer(monster, -1.0);
        boolean farFromEveryPlayer = nearest == null || monster.distanceTo(nearest) > FeralRules.despawnRadius();
        if (!farFromEveryPlayer) {
            timers.remove(monster.getUUID());
            return;
        }

        TickDelay timer = timers.computeIfAbsent(monster.getUUID(),
                id -> new TickDelay(FeralRules.despawnLingerTicks(), level::getGameTime));
        if (!timer.allow()) {
            return;
        }
        monster.discard();
        timers.remove(monster.getUUID());
    }

    /**
     * A pack member's despawn: silent ({@code discard()}, no death event) and direction-aware. One ahead
     * of the nearest player is kept out to {@link FeralRules#packDespawnRadius()}, so a distant but real
     * ambush is not destroyed for not being reached yet; one that has fallen behind only to
     * {@link FeralRules#packBehindDespawnRadius()}. Raw distance stops mattering once the player has
     * passed a mob: with the speed boost it can trail a sprinter indefinitely without ever covering a
     * large radius, quietly eating the cap.
     */
    private static void despawnIfBeyondPackRadius(Level level, Mob monster) {
        Player nearest = level.getNearestPlayer(monster, -1.0);
        if (nearest == null) {
            monster.discard();
            return;
        }

        boolean ahead = FeralPackSpawner.isAheadOfPlayer(monster, nearest.position(), nearest.getYRot());
        double radius = ahead ? FeralRules.packDespawnRadius() : FeralRules.packBehindDespawnRadius();
        if (monster.distanceTo(nearest) <= radius) {
            return;
        }
        monster.discard();
    }

    /**
     * An ambient (non-pack) Feral mob's despawn: once it is farther than
     * {@link FeralRules#ambientDespawnRadius()} from every player it was never going to be encountered
     * and only sits on the cap. A flat distance check, no direction, no linger.
     */
    private static void despawnIfBeyondAmbientRadius(Level level, Mob monster) {
        Player nearest = level.getNearestPlayer(monster, -1.0);
        boolean farFromEveryPlayer = nearest == null || monster.distanceTo(nearest) > FeralRules.ambientDespawnRadius();
        if (!farFromEveryPlayer) {
            return;
        }
        monster.discard();
    }

    /**
     * Kills a Feral mob that re-entered the Frontier. A Slime or MagmaCube (a {@code Slime}) splits into
     * 2-4 smaller children of the same type inside {@code remove()} whenever it is dead and larger than
     * the smallest size, independent of the loot cancellation, so the children already exist when
     * {@code kill()} returns and are discarded here in the same tick -- an artifact of the parent's own
     * death, not a second encounter.
     */
    private static void killAndCleanupSplit(Level level, Mob monster) {
        BlockPos pos = monster.blockPosition();
        boolean splits = monster instanceof Slime;

        monster.kill();

        if (!splits) {
            return;
        }
        for (Slime child : level.getEntitiesOfClass(Slime.class, new AABB(pos).inflate(2.0))) {
            if (child.getType() == monster.getType()) {
                child.discard();
            }
        }
    }

    /**
     * At most once a second per level, and only when a Feral mob exists: the counts of pack members, all
     * Feral and all mobs, and per player in the Wilds whether they sprint, their frontier distance, and
     * the distance to their nearest pack member. Switched off ({@link #DIAGNOSTIC_LOGGING_ENABLED}). Takes a
     * {@code ServerLevel} because {@code getAllEntities()} is the server's.
     */
    static void logPackDiagnostics(ServerLevel level, FeralPresence presence) {
        if (!DIAGNOSTIC_LOGGING_ENABLED) {
            return;
        }
        if (!presence.diagnosticsThrottler(level).allow() || presence.count() == 0) {
            return;
        }

        int totalEntities = 0;
        int totalMobs = 0;
        for (Entity entity : level.getAllEntities()) {
            totalEntities++;
            if (entity instanceof Mob) {
                totalMobs++;
            }
        }

        List<Mob> packMembers = new ArrayList<>();
        int packMembersTargetingPlayer = 0;
        for (Mob mob : presence.mobs()) {
            if (presence.isPack(mob.getUUID())) {
                packMembers.add(mob);
                if (mob.getTarget() instanceof Player) {
                    packMembersTargetingPlayer++;
                }
            }
        }

        StringBuilder perPlayer = new StringBuilder();
        for (Player player : level.players()) {
            double nearestPackDist = Double.NaN;
            for (Mob mob : packMembers) {
                double d = mob.distanceTo(player);
                if (Double.isNaN(nearestPackDist) || d < nearestPackDist) {
                    nearestPackDist = d;
                }
            }
            perPlayer.append(player.getGameProfile().getName())
                    .append("[frontierDist=").append(BorderAPI.frontierDistanceAt(level, player.blockPosition()))
                    .append(",sprinting=").append(player.isSprinting())
                    .append(",nearestPack=")
                    .append(Double.isNaN(nearestPackDist) ? "none" : String.format("%.1f", nearestPackDist))
                    .append("] ");
        }

        OUT.info("[Feral][Diag] totalEntities=" + totalEntities + " totalMobs=" + totalMobs
                + " allFeral=" + presence.count() + " packMembers=" + packMembers.size()
                + " packTargetingPlayer=" + packMembersTargetingPlayer + " | " + perPlayer);
    }
}
