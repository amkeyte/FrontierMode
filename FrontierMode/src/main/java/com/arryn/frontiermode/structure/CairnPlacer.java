package com.arryn.frontiermode.structure;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.BorderMath;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.keeper.KeeperAPI;
import com.arryn.frontiermode.keeper.common.fixture.KeeperRecord;
import com.arryn.frontiermode.siting.SitingAPI;
import com.arryn.frontiermode.structure.common.CairnFixture;
import com.arryn.frontiermode.structure.common.CairnPlacement;
import com.arryn.frontiermode.structure.common.CairnRecord;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;

/**
 * Places a Cairn: one candidate spot per Cairn per tick, built when the spot passes every rule. Tuning
 * ("safe baseline, replace later", same framing as every other tunable in this codebase) is this
 * module's own build decisions, not part of any wiki-ratified contract.
 */
final class CairnPlacer {

    static final int PAD_SIZE = 3;
    static final int PILLAR_HEIGHT = 3; // 2 Mossy Cobblestone + 1 cap
    private static final int FOOTPRINT_CLEARANCE = SitingAPI.FOOTPRINT_CLEARANCE; // the one shared number (siting.md)

    /**
     * Draws placement candidates and the dead-boss pulse sphere's point, on the server, and nothing is
     * derived from it later.
     */
    static final RandomSource RNG = RandomSource.create();

    private CairnPlacer() {
    }

    /**
     * One candidate spot per Cairn per tick (cairn.md § Placement): a point 50-100% of the boss's home
     * Border radius from the boss. A candidate that fails any rule is dropped and a new point is
     * sampled next tick; after {@code cairnMaxAttempts} of them the Cairn gives up.
     */
    static void attemptPlacement(Level level, CairnFixture fixture, CairnRecord record) {
        Optional<BossRecord> bossOpt = BossAPI.bosses(level).flatMap(f -> f.get(record.bossId()));
        Optional<KeeperRecord> keeperOpt = KeeperAPI.keepers(level).flatMap(f -> f.get(record.bossId()));
        // An entry without its Keeper record is not whole: it waits.
        if (bossOpt.isEmpty() || keeperOpt.isEmpty()) {
            return;
        }
        BossRecord boss = bossOpt.get();
        KeeperRecord keeper = keeperOpt.get();
        Optional<Border> homeOpt = keeper.borderId()
                .flatMap(id -> BorderAPI.CRUD(level).flatMap(c -> c.get(id)));
        // bordersContaining throws while the Border facet isn't resolvable; stand by instead.
        if (homeOpt.isEmpty() || BorderAPI.RULES(level).isEmpty()) {
            return;
        }

        int radius = homeOpt.get().radius();
        BlockPos candidate = BorderMath.randomPointInAnnulus(RNG, boss.position(),
                CairnPlacement.innerRadius(radius), CairnPlacement.outerRadius(radius));

        Optional<BlockPos> ground = suitableGround(level, candidate);
        if (ground.isEmpty()) {
            rejectCandidate(fixture, record, keeper);
            return;
        }

        buildStructure(level, ground.get());
        fixture.updateAnchor(record.cairnId(), ground.get());

        OUT.info("[Structure] Cairn " + Ids.shortId(record.cairnId()) + " placed for boss "
                + Ids.shortId(record.bossId()) + " at " + ground.get().getX() + ", "
                + ground.get().getY() + ", " + ground.get().getZ());
    }

    /**
     * The ground to build on at {@code candidate}, or empty if the spot fails a rule. In order, cheapest
     * first: it is claimed ground (a Border contains it -- never the Wilds) whose terrain is generated
     * (a containing Border reports {@code isPregenReady}); then its footprint's chunks are loaded --
     * from disk, since the terrain already exists -- and the shared terrain-suitability check runs.
     */
    private static Optional<BlockPos> suitableGround(Level level, BlockPos candidate) {
        boolean claimedAndGenerated = BorderAPI.bordersContaining(level, candidate).stream()
                .anyMatch(border -> BorderAPI.isPregenReady(level, border.id()));
        if (!claimedAndGenerated) {
            return Optional.empty();
        }

        // Unlike Boss's own materialization, which waits on Level.isLoaded, a Cairn can sit most of a
        // Border radius from any player: load the (at most four) chunks rather than wait for a visit.
        for (ChunkPos chunk : CairnPlacement.footprintChunks(candidate, PAD_SIZE)) {
            level.getChunk(chunk.x, chunk.z);
        }
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidate);
        return SitingAPI.footprintSuitable(level, ground, PAD_SIZE, FOOTPRINT_CLEARANCE)
                ? Optional.of(ground)
                : Optional.empty();
    }

    /** Counts a dropped candidate against {@code record}; at {@code cairnMaxAttempts} the Cairn gives up. */
    private static void rejectCandidate(CairnFixture fixture, CairnRecord record, KeeperRecord keeper) {
        int failed = fixture.recordFailedCandidate(record.cairnId());
        int max = CairnRules.cairnMaxAttempts();
        if (failed < max) {
            return;
        }

        fixture.remove(record.cairnId());
        long placed = fixture.forBoss(keeper.bossId()).stream().filter(CairnRecord::placed).count();
        OUT.warn("[Structure] A Cairn for boss " + Ids.shortId(keeper.bossId()) + " found no ground after "
                + max + " candidates and was dropped; " + placed + " of "
                + CairnPlacement.count(keeper.pathPosition()) + " placed.");
    }

    /**
     * Places the pad's floor blocks and clears the air above them. Every pad cell is built at the
     * <em>center</em> candidate's resolved ground height, but {@code footprintSuitable}'s own
     * flatness tolerance allows real height variation across the 3x3 area -- so on uneven terrain,
     * a non-center cell's original, taller ground can leave solid leftover blocks sitting right
     * above the new pad surface, which a debug-teleported player standing on an edge cell would
     * suffocate in. Matches {@code BossPlatform.place} (Environmental Tells' own 3x3 platform),
     * which clears air above every cell for the identical reason, not just the one the pillar
     * sits on.
     */
    private static void buildStructure(Level level, BlockPos ground) {
        int half = PAD_SIZE / 2;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                BlockPos cell = ground.offset(dx, 0, dz);
                level.setBlock(cell, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
                for (int dy = 1; dy <= FOOTPRINT_CLEARANCE; dy++) {
                    level.setBlock(cell.above(dy), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        level.setBlock(ground.above(1), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
        level.setBlock(ground.above(2), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
        level.setBlock(ground.above(PILLAR_HEIGHT), StructureModule.CAIRN_CAP.get().defaultBlockState(), 2);
    }
}
