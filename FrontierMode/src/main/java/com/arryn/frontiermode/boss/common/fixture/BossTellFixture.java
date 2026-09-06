package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.BorderMath;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BorderCurve;
import com.arryn.frontiermode.border.common.fixture.BorderCurveFixture;
import com.arryn.frontiermode.border.common.fixture.Shape;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
//import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates a {@code "tell"}-purpose {@link BorderCurve} (LOG shape, steepness 1.0) for
 * {@code borderId} if none exists yet. Idempotent -- a no-op after a server restart.
 * Called at both boss-creation call sites in {@code BossModule}.
 */

/**
 * Sibling fixture to {@link BossFixture} inside
 * {@link com.arryn.frontiermode.boss.common.bundle.BossBundle} -- drives the per-boss
 * environmental tells (particle and sound) and the 3×3 bedrock platform, per
 * wiki/frontiermode/architecture/discovery-systems.md#environmental-tells.
 *
 * <p><b>Two jobs, one tick:</b>
 * <ol>
 *   <li><b>Platform pass (every tick):</b> for any boss record without a platform whose boss
 *       position is known and chunk-loaded, places a 3×3 bedrock platform one block below the
 *       boss and clears {@value #AIR_CLEARANCE} blocks of air above it to prevent mobs spawning
 *       into tree foliage. Once placed the record is stamped and this pass skips it forever.
 *   <li><b>Tell pass (cadence-gated via {@link com.arryn.frontiermode.boss.server.rules.BossRules#tellTickInterval()}):</b>
 *       for each server player on this level, finds the nearest alive+positioned boss with a
 *       borderId, resolves its {@code "tell"}-purpose {@link BorderCurve} (LOG shape), computes
 *       intensity via {@link BorderMath#intensityAt(Border, BorderCurve, BlockPos)}, then makes
 *       two <em>independent</em> probability rolls: one for a particle spawn near the player, one
 *       for an ambient sound broadcast. Stateless: no attunement state, always "nearest right
 *       now" per player per interval.
 * </ol>
 *
 * <p>The fixture itself never calls {@link BorderMath} via {@code BorderAPI.MATH} (that field is
 * declared but never initialized -- see FRO_088) -- it calls {@link BorderMath#intensityAt}
 * directly, which is public static and fully operational.
 */
public final class BossTellFixture extends SatchelFixture {
    public static void createTellCurveIfAbsent(Level level, UUID borderId) {
        BorderAPI.CURVE(level).ifPresent(curves -> {
            if (curves.forBorder(borderId, "tell").isEmpty()) {
                curves.create(borderId, "tell", Shape.LOG, 1.0);
            }
        });
    }

    private static final String KEY_TELLS = "tells";

    // Shared RNG -- same static-field shape BossRulesFacet.RULES already uses (one instance
    // shared across every level's BossTellFixture). Not persisted; resets on server restart,
    // which is fine: we just skip one early-interval window.
    private static final RandomSource RNG = RandomSource.create();

    // Horizontal spread (±blocks) for randomizing the particle spawn position near the player.
    private static final int PARTICLE_SPREAD = 5;

    // How many blocks of air to clear directly above each bedrock cell in the platform.
    // Prevents mobs from spawning into tree foliage that can sit at ground level.
    private static final int AIR_CLEARANCE = 4;
    private final List<BossTellRecord> records = new ArrayList<>();
    // Non-persisted tell-pass cadence counter. Resets on server restart; the first partial
    // interval is silently skipped -- "off/on signal" discipline, no harm done.
    private int tickCounter = 0;

    public BossTellFixture() {
        registerCustom(KEY_TELLS, this::saveTells, this::loadTells);
    }

    /**
     * Places a 3×3 bedrock layer at {@code bossPos.below()} and clears {@value #AIR_CLEARANCE}
     * blocks of air above each bedrock cell.
     *
     * <p>{@code bossPos} is the boss's standing position (the air block the mob is at, per
     * {@code MOTION_BLOCKING_NO_LEAVES} heightmap semantics). The bedrock goes one block below
     * that, the air clearance runs from bossPos upward.
     */
    private static void placePlatform(Level level, BlockPos bossPos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                // Bedrock cell: one below boss standing position.
                BlockPos bedrockCell = bossPos.offset(dx, -1, dz);
                level.setBlock(bedrockCell, Blocks.BEDROCK.defaultBlockState(), 3);
                // Air clearance above the platform (boss Y through boss Y + AIR_CLEARANCE - 1).
                for (int dy = 0; dy < AIR_CLEARANCE; dy++) {
                    level.setBlock(bedrockCell.above(dy + 1), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    /**
     * Spawns a small tell particle at a randomized position near {@code playerPos}, after a short
     * heightmap ground check. Not BorderPregen's full column check -- just a single heightmap
     * lookup per the discovery-systems spec ("short heightmap ground check").
     */
    private static void spawnTellParticle(Level level, Player player, BlockPos playerPos) {
        int ox = RNG.nextInt(PARTICLE_SPREAD * 2 + 1) - PARTICLE_SPREAD;
        int oz = RNG.nextInt(PARTICLE_SPREAD * 2 + 1) - PARTICLE_SPREAD;
        BlockPos groundPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(playerPos.getX() + ox, 0, playerPos.getZ() + oz));
        // Targeted send so only this player sees it -- the particle is near them specifically.
        level.sendParticles(player, ParticleTypes.SMOKE, false, groundPos.getX() + 0.5, groundPos.getY() + 0.5, groundPos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.01);
    }

    private void saveTells(CompoundTag root) {
        Satchel.requireServer();
        ListTag list = new ListTag();
        for (BossTellRecord r : records) {
            list.add(BossTellRecord.save(r));
        }
        root.put(KEY_TELLS, list);
    }

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------

    private void loadTells(CompoundTag root) {
        records.clear();
        if (!root.contains(KEY_TELLS, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = root.getList(KEY_TELLS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            // Same skip-malformed discipline BossFixture.loadBosses already establishes.
            try {
                records.add(BossTellRecord.load((CompoundTag) t));
            } catch (RuntimeException e) {
                OUT.warn("[BossTell] Skipping malformed tell record during load: " + e);
            }
        }
    }

    @Override
    public void onLoaded() {
        super.onLoaded();
        OUT.info("            BossTellRecords loaded: " + records.size());
    }

    // ------------------------------------------------------------------
    // Tick (called from BossModule.onBossJigTick every server tick)
    // ------------------------------------------------------------------

    /**
     * Seeds a new tell record for {@code bossId} with a null platform -- called at boss creation
     * time. The platform itself is placed later by this fixture's own tick, once the boss's chunk
     * is loaded. Idempotent: a duplicate call for the same {@code bossId} is a logged no-op, not
     * an error.
     */
    public void createRecord(UUID bossId) {
        Satchel.requireServer();
        boolean exists = records.stream().anyMatch(r -> r.bossId().equals(bossId));
        if (exists) {
            OUT.warn("[BossTell] createRecord(): record already exists for bossId=" + bossId + " -- ignoring.");
            return;
        }
        records.add(new BossTellRecord(bossId, null));
        markDirty();
    }

    // ------------------------------------------------------------------
    // Platform building
    // ------------------------------------------------------------------

    private void setPlatformPos(UUID bossId, BlockPos pos) {
        Satchel.requireServer();
        Optional<BossTellRecord> existing = records.stream().filter(r -> r.bossId().equals(bossId)).findFirst();
        if (existing.isEmpty()) {
            OUT.warn("[BossTell] setPlatformPos(): no tell record for bossId=" + bossId + " -- ignoring.");
            return;
        }
        // Immutable swap -- same remove-then-add-back shape BossFixture's own mutations use.
        records.removeIf(r -> r.bossId().equals(bossId));
        records.add(existing.get().withPlatform(pos));
        markDirty();
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    public void onJigTick() {
        Satchel.requireServer();
        BossBundle bundle = getBundle();
        Level level = bundle.level();
        //if (!(level instanceof ServerLevel serverLevel)) return;
        bundle.boss().ifPresent(boss ->
                BorderAPI.CURVE(level).ifPresent(curves -> tick(level, boss, curves)));
    }

    private void tick(Level level, BossFixture bossFixture, BorderCurveFixture curveFixture) {
        Satchel.requireServer();

        // Platform pass runs every tick -- chunk-load timing is the gating signal.
        buildPendingPlatforms(level, bossFixture);

        // Tell pass is cadence-gated.
        tickCounter++;
        int interval = bossFixture.RULES.tellTickInterval();
        if (tickCounter < interval) {
            return;
        }
        tickCounter = 0;

        runTellsForPlayers(level, bossFixture, curveFixture);
    }

    // ------------------------------------------------------------------
    // Tell pass
    // ------------------------------------------------------------------

    private void buildPendingPlatforms(Level level, BossFixture bossFixture) {
        for (BossTellRecord tellRecord : List.copyOf(records)) {
            if (tellRecord.hasPlatform()) {
                continue;
            }

            // The boss record's position is null until border pregeneration finishes and
            // BOSS_JIG's own tick finalizes it -- skip until that point.
            Optional<BossRecord> bossRecord = bossFixture.get(tellRecord.bossId());
            if (bossRecord.isEmpty() || !bossRecord.get().positioned()) {
                continue;
            }

            BlockPos bossPos = bossRecord.get().position();
            if (!level.isLoaded(bossPos)) {
                // "All a Tell needs to know is off or on." -- arch page's own framing.
                // No-op this tick; retried automatically next tick.
                continue;
            }

            placePlatform(level, bossPos);
            // Record the base of the bedrock layer (one below the boss standing position).
            setPlatformPos(tellRecord.bossId(), bossPos.below());
            OUT.info("[BossTell] Built 3×3 bedrock platform for bossId=" + tellRecord.bossId() + " centered at " + bossPos.below());
        }
    }

    private void runTellsForPlayers(Level level, BossFixture bossFixture, BorderCurveFixture curveFixture) {

        // Alive+positioned bosses with a borderId are the only candidates.
        List<BossRecord> candidates = bossFixture.all().stream().filter(r -> r.alive() && r.positioned() && r.borderId().isPresent()).toList();

        if (candidates.isEmpty()) {
            return;
        }

        var crudOpt = BorderAPI.CRUD(level);
        if (crudOpt.isEmpty()) {
            return;
        }
        var crud = crudOpt.get();

        double particleCoeff = bossFixture.RULES.tellParticleCoefficient();
        double soundCoeff = bossFixture.RULES.tellSoundCoefficient();

        for (Player player : level.players()) {
            BlockPos playerPos = player.blockPosition();

            // Nearest alive+positioned boss to this player -- stateless, resolved fresh every
            // interval. "Always nearest right now," per the discovery-systems spec.
            BossRecord nearest = null;
            double nearestDistSq = Double.MAX_VALUE;
            for (BossRecord r : candidates) {
                double distSq = r.position().distSqr(playerPos);
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = r;
                }
            }
            if (nearest == null) {
                continue;
            }

            UUID borderId = nearest.borderId().get(); // present by construction of `candidates`

            // Resolve border and tell curve.
            Optional<Border> borderOpt = crud.get(borderId);
            if (borderOpt.isEmpty()) {
                continue;
            }
            Optional<BorderCurve> curveOpt = curveFixture.forBorder(borderId, "tell");
            if (curveOpt.isEmpty()) {
                continue;
            }

            // Intensity: 0.0 (player at border edge) → 1.0 (player at boss position).
            // Calls BorderMath directly -- BorderAPI.MATH is declared but never initialized
            // (FRO_088), so we bypass it and use the public static method directly.
            double intensity = BorderMath.intensityAt(borderOpt.get(), curveOpt.get(), playerPos);
            if (intensity <= 0.0) {
                continue;
            }

            // Two independent probability rolls, one coefficient each.
            if (RNG.nextDouble() < intensity * particleCoeff) {
                spawnTellParticle(level, player, playerPos);
            }
            if (RNG.nextDouble() < intensity * soundCoeff) {
                // null = broadcast to all nearby players (area broadcast, per discovery-systems
                // spec). Volume intentionally low; placeholder sound -- real tuning is playtest
                // territory.
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.5f, 0.8f + RNG.nextFloat() * 0.4f);
            }
        }
    }
}
