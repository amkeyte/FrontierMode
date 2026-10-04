package com.arryn.frontiermode.siting;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.server.rules.BorderRules;
import com.arryn.frontiermode.siting.common.SitingMath;
import com.arryn.frontiermode.siting.server.rules.SitingRules;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * The new-world starting-site search (siting.md § The starting site): where the world's spawn, and so
 * its first Border, goes. Candidates are vanilla's own spawn first, then rings outward from it; the
 * first that passes the full check wins, and a search that finds none takes the best one it checked.
 * Runs once, at world creation, so it generates terrain synchronously and touches no scope or fixture.
 */
final class StartingSite {

    // The first Border's outer share the Keeper-spot check samples, as the Keeper's own placement does.
    private static final double KEEPER_INNER_FRACTION = 0.6;
    private static final int KEEPER_SAMPLES = 20;
    // The pre-filter's rim points, around a center: the compass points and diagonals.
    private static final int[][] RIM = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};

    private StartingSite() {
    }

    /** The chosen spawn for a brand-new world. Never null: vanilla's own spawn is the last resort. */
    static BlockPos search(ServerLevel level) {
        SitingRules rules = SitingRules.ACTIVE;
        long started = System.nanoTime();

        BlockPos vanilla = vanillaSpawn(level);
        int radius = BorderRules.ACTIVE.radiusForPathPosition(level, 1);
        SitingMath.Requirements need = new SitingMath.Requirements(
                rules.startingSiteMinLogs(), rules.startingSiteMaxWater(), rules.startingSiteMinStoneColumns());
        int maxChecks = rules.startingSiteMaxFullChecks();

        BlockPos best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int checks = 0;
        int examined = 0;

        List<BlockPos> candidates = new ArrayList<>();
        candidates.add(vanilla);
        candidates.addAll(SitingAPI.ringCandidates(vanilla, rules.startingSiteRingStep(), rules.startingSiteSearchLimit()));

        OUT.info("[Siting] starting-site search begins: vanilla spawn " + vanilla.getX() + ", " + vanilla.getY()
                + ", " + vanilla.getZ() + " (" + (System.nanoTime() - started) / 1_000_000L + " ms to find it); "
                + candidates.size() + " candidates, disc radius " + radius + ", full-check cap " + maxChecks);

        for (int i = 0; i < candidates.size() && checks < maxChecks; i++) {
            // Each candidate's own standable spot is found first, so the disc judged is the disc the
            // first Border will cover. Vanilla's candidate already is one.
            long t0 = System.nanoTime();
            BlockPos spot = i == 0 ? vanilla : spawnSpotNear(level, candidates.get(i));
            long spotMs = millisSince(t0);
            if (spot == null) {
                OUT.info("[Siting]   #" + i + " no standable spot in its chunk (" + spotMs + " ms) -- skipped");
                continue;
            }
            examined++;
            long t1 = System.nanoTime();
            boolean wet = mostlyWater(level, spot, radius);
            long filterMs = millisSince(t1);
            if (wet) {
                OUT.info("[Siting]   #" + i + " " + describe(spot) + " water-heavy by biome, skipped (spot " + spotMs
                        + " ms, pre-filter " + filterMs + " ms; " + elapsed(started) + " ms in)");
                continue;
            }
            checks++;
            Measured measured = read(level, spot, radius, rules);
            SitingMath.Verdict verdict = SitingMath.judge(measured.reading(), need);
            SitingMath.Reading r = measured.reading();
            OUT.info("[Siting]   #" + i + " " + describe(spot) + " full check " + checks + "/" + maxChecks + ": "
                    + (verdict.passes() ? "PASS" : "fail") + " score " + String.format("%.2f", verdict.score())
                    + " (logs " + r.logs() + ", water " + String.format("%.0f%%", r.waterFraction() * 100)
                    + ", stone columns " + r.stoneColumns() + ", keeper spot " + r.keeperSpot() + ") -- spot "
                    + spotMs + " ms, pre-filter " + filterMs + " ms, generate " + measured.generateMs()
                    + " ms, scan " + measured.scanMs() + " ms; " + elapsed(started) + " ms in");
            if (verdict.passes()) {
                logResult("passed", spot, i, examined, checks, started);
                return spot;
            }
            if (verdict.score() > bestScore) {
                bestScore = verdict.score();
                best = spot;
            }
        }

        BlockPos chosen = best != null ? best : vanilla;
        logResult(best != null ? "none passed; best checked" : "none checked; vanilla's spawn", chosen,
                -1, examined, checks, started);
        return chosen;
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static long elapsed(long startNanos) {
        return millisSince(startNanos);
    }

    private static String describe(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    /** What a full check measured, and how long its two halves took: generating the disc, then scanning it. */
    private record Measured(SitingMath.Reading reading, long generateMs, long scanMs) {
    }

    private static void logResult(String outcome, BlockPos spot, int index, int examined, int checks, long started) {
        OUT.info("[Siting] starting site " + outcome + " at " + spot.getX() + ", " + spot.getY() + ", "
                + spot.getZ() + " (candidate " + (index < 0 ? "-" : index) + ", " + examined + " examined, "
                + checks + " full checks, " + (System.nanoTime() - started) / 1_000_000L + " ms total)");
    }

    // ── candidates ───────────────────────────────────────────────────────────

    /**
     * Vanilla's own spawn, computed as vanilla does ({@code MinecraftServer.setInitialSpawn}): the chunk
     * from the climate sampler's spawn search, then an 11 x 11 chunk spiral of
     * {@code getSpawnPosInChunk} for the first standable spot. If it passes, the player starts exactly
     * where vanilla would have put them.
     */
    private static BlockPos vanillaSpawn(ServerLevel level) {
        ServerChunkCache chunkSource = level.getChunkSource();
        ChunkPos start = new ChunkPos(chunkSource.randomState().sampler().findSpawnPosition());
        int y = chunkSource.getGenerator().getSpawnHeight(level);
        if (y < level.getMinBuildHeight()) {
            BlockPos world = start.getWorldPosition();
            y = level.getHeight(Heightmap.Types.WORLD_SURFACE, world.getX() + 8, world.getZ() + 8);
        }
        BlockPos fallback = start.getWorldPosition().offset(8, y, 8);

        int dx = 0;
        int dz = 0;
        int stepX = 0;
        int stepZ = -1;
        for (int i = 0; i < 11 * 11; i++) {
            if (dx >= -5 && dx <= 5 && dz >= -5 && dz <= 5) {
                BlockPos spot = PlayerRespawnLogic.getSpawnPosInChunk(
                        level, new ChunkPos(start.x + dx, start.z + dz));
                if (spot != null) {
                    return spot;
                }
            }
            if (dx == dz || (dx < 0 && dx == -dz) || (dx > 0 && dx == 1 - dz)) {
                int turn = stepX;
                stepX = -stepZ;
                stepZ = turn;
            }
            dx += stepX;
            dz += stepZ;
        }
        return fallback;
    }

    /** The first standable spawn spot in {@code candidate}'s chunk, or null if the chunk has none. */
    private static BlockPos spawnSpotNear(ServerLevel level, BlockPos candidate) {
        return PlayerRespawnLogic.getSpawnPosInChunk(level, new ChunkPos(candidate));
    }

    // ── the free pre-filter ──────────────────────────────────────────────────

    /**
     * Biomes read from the biome source at the center and eight rim points, which needs no chunk
     * generation. More than half of the nine in ocean, river or beach and the candidate is skipped; this
     * is what keeps a water-heavy seed affordable.
     */
    private static boolean mostlyWater(ServerLevel level, BlockPos center, int radius) {
        int wet = 0;
        int y = level.getSeaLevel();
        if (isWaterBiome(level.getBiome(new BlockPos(center.getX(), y, center.getZ())))) {
            wet++;
        }
        for (int[] d : RIM) {
            int x = center.getX() + (int) Math.round(d[0] * radius * (d[0] != 0 && d[1] != 0 ? Math.sqrt(0.5) : 1.0));
            int z = center.getZ() + (int) Math.round(d[1] * radius * (d[0] != 0 && d[1] != 0 ? Math.sqrt(0.5) : 1.0));
            if (isWaterBiome(level.getBiome(new BlockPos(x, y, z)))) {
                wet++;
            }
        }
        return wet * 2 > 9;
    }

    private static boolean isWaterBiome(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH);
    }

    // ── the full check ───────────────────────────────────────────────────────

    /**
     * Generates the first Border's disc around {@code center} (about 25 chunks, to full status,
     * synchronously: the world is still being created) and measures it: logs, the share of water-surface
     * columns, columns with natural stone close to the surface, and whether one of 20 points in the disc's
     * outer share is a hazard-free spot for the first Keeper.
     */
    private static Measured read(ServerLevel level, BlockPos center, int radius, SitingRules rules) {
        long generateStart = System.nanoTime();
        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                level.getChunk(cx, cz);
            }
        }
        long generateMs = millisSince(generateStart);
        long scanStart = System.nanoTime();

        int logDepth = rules.startingSiteLogDepth();
        int stoneDepth = rules.startingSiteStoneDepth();
        int logs = 0;
        int waterColumns = 0;
        int stoneColumns = 0;
        int columns = 0;
        long radiusSq = (long) radius * radius;

        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                long dx = x - center.getX();
                long dz = z - center.getZ();
                if (dx * dx + dz * dz > radiusSq) {
                    continue;
                }
                columns++;

                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                if (!level.getFluidState(new BlockPos(x, surfaceY - 1, z)).isEmpty()) {
                    waterColumns++;
                }
                for (int y = surfaceY - 1; y >= surfaceY - logDepth && y >= level.getMinBuildHeight(); y--) {
                    if (level.getBlockState(new BlockPos(x, y, z)).is(BlockTags.LOGS)) {
                        logs++;
                    }
                }

                int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                for (int y = groundY - 1; y >= groundY - 1 - stoneDepth && y >= level.getMinBuildHeight(); y--) {
                    if (level.getBlockState(new BlockPos(x, y, z)).is(BlockTags.BASE_STONE_OVERWORLD)) {
                        stoneColumns++;
                        break;
                    }
                }
            }
        }

        double waterFraction = columns == 0 ? 1.0 : waterColumns / (double) columns;
        SitingMath.Reading reading =
                new SitingMath.Reading(logs, waterFraction, stoneColumns, hasKeeperSpot(level, center, radius));
        return new Measured(reading, generateMs, millisSince(scanStart));
    }

    /** At least one of 20 points in the disc's outer share scores a hazard below 1, as a Keeper's home does. */
    private static boolean hasKeeperSpot(ServerLevel level, BlockPos center, int radius) {
        RandomSource rng = level.getRandom();
        int inner = Math.min(radius, (int) Math.round(radius * KEEPER_INNER_FRACTION));
        for (int i = 0; i < KEEPER_SAMPLES; i++) {
            BlockPos sample = BorderAPI.MATH.randomPointInAnnulus(rng, center, inner, radius);
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sample);
            if (SitingAPI.hazardScore(level, ground) < 1.0) {
                return true;
            }
        }
        return false;
    }
}
