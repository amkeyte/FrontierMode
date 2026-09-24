package com.arryn.frontiermode.structure;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.BorderMath;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.boss.BossPositionListeners;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import com.arryn.frontiermode.structure.common.CairnCapBlock;
import com.arryn.frontiermode.structure.common.CairnFixture;
import com.arryn.frontiermode.structure.common.CairnRecord;
import com.arryn.frontiermode.structure.common.StructureBundle;
import com.arryn.frontiermode.structure.server.commands.StructureCommands;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.lifecycle.BlockBreakSignal;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Entry point for {@code StructureModule} (FRO_120, RM_FRO_039 "Helen") -- Cairn's own third
 * home, not {@code BossBundle} and not {@code BossTellModule}, per
 * wiki/frontiermode/architecture/cairn.md#module-structuremodule-not-bossbundle-or-bosstellmodule.
 * Reaches {@code BossAPI}/{@code BorderAPI} from outside, the same one-directional relationship
 * Feral/BossTellModule already have with both -- Boss/Border never import this module. See
 * {@link com.arryn.frontiermode.boss.BossPositionListeners} for how record creation still reacts
 * to a boss's position finalizing without Boss importing this module directly.
 */
public final class StructureModule {

    private StructureModule() {
    }

    // ------------------------------------------------------------
    // Tuning ("safe baseline, replace later", same framing as every other tunable in this
    // codebase) -- not part of any wiki-ratified contract, this module's own build decisions.
    // ------------------------------------------------------------

    private static final int QUALIFYING_BORDER_RADIUS = 200;
    private static final double PLACEMENT_RADIUS_FRACTION = 0.30;
    private static final int PAD_SIZE = 3;
    private static final int PILLAR_HEIGHT = 3; // 2 Mossy Cobblestone + 1 cap
    private static final int FOOTPRINT_CLEARANCE = 5; // shared number, per boss.md's own spec
    private static final int DEAD_SPHERE_RADIUS = 100;

    // Capstone explosion power. Playtest found 1.0F effectively harmless -- Level.explode()'s
    // entity-damage/knockback pass only touches entities within (power * 2) blocks of the
    // center, so at 1.0F only something standing almost exactly on top of the cap felt anything
    // at all; ExplosionInteraction.NONE (block-damage-free) doesn't affect that pass, only
    // whether blocks themselves get destroyed. 2.0F widens the hit radius to 4 blocks and scales
    // up damage/knockback with it -- still well under a Creeper's 3.0F or TNT's 4.0F. Damage
    // scales sharply with this value and further with distance/armor, so "a heart and some
    // knockback" at typical break range is a playtest estimate, not a guarantee -- tune further
    // if it still reads as too weak (or too strong) in practice.
    private static final float CAPSTONE_EXPLOSION_POWER = 2.0F;

    private static final RandomSource RNG = RandomSource.create();

    // ------------------------------------------------------------
    // Block registration -- Chiseled Smooth Basalt is a new asset, out of scope (cairn.md); the
    // real block copies vanilla Chiseled Stone Bricks' properties as the stand-in, per FRO_120's
    // own ticket scope.
    // ------------------------------------------------------------

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, FrontierMode.MODID);

    public static final RegistryObject<Block> CAIRN_CAP =
            BLOCKS.register("cairn_cap", () -> new CairnCapBlock(
                    BlockBehaviour.Properties.copy(Blocks.CHISELED_STONE_BRICKS).noLootTable()));

    public static void init() {
        OUT.info("FrontierMode - StructureModule Initiating.");
        registerStructureJig();
        BossPositionListeners.register(StructureModule::onBossPositioned);
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        StructureCommands.register(event.getDispatcher());
    }

    // ------------------------------------------------------------
    // Jig wiring
    // ------------------------------------------------------------

    private static void registerStructureJig() {
        var cairnFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.CAIRN,
                        CairnFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var structureBundle =
                new JigBundles.BundleDecl<>(
                        FrontierKeys.STRUCTURE_BUNDLE,
                        (LevelScope scope) -> new StructureBundle(scope, FrontierKeys.STRUCTURE_BUNDLE),
                        List.of(cairnFixture)
                );

        JigBundles.Schema<LevelScope> schema =
                new JigBundles.Schema<>(List.of(structureBundle));

        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Tick.class, StructureModule::onTick)
                        // FRO_120: the capstone explosion's block-break detection -- a bare,
                        // unscoped signal like ItemPickupSignal (see BossTellModule.init() for
                        // the identical precedent/reasoning: declaring it here is just where
                        // installation happens to occur, not a claim this is STRUCTURE_JIG-scoped).
                        .on(BlockBreakSignal.class, StructureModule::onBlockBreak)
                        .build();

        LevelJigConfig config = new LevelJigConfig(FrontierKeys.STRUCTURE_JIG);
        config.bundles().schema(schema);
        // CairnFixture is real, persisted, world-anchored state -- both calls needed explicitly,
        // the same two-call requirement boss.md's own "Module wiring" flags as having been
        // missed once already (FRO_014).
        config.policies().capabilities(new JigPolicies.Capabilities(true, false, false));
        config.policies().persistence(
                new JigPolicies.Persistence(
                        true,
                        JigPolicies.FlushPolicy.EXECUTION_PULSE,
                        JigPolicies.MissingDataPolicy.WARN));
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true).withExecutionPulse(true))
                .eventHandlers(eventHandlers);

        Satchel.registerJigConfig(config);
    }

    // ------------------------------------------------------------
    // Record creation -- "the moment a qualifying boss's own position commits" (cairn.md).
    // ------------------------------------------------------------

    private static void onBossPositioned(Level level, BossRecord record) {
        Optional<UUID> borderIdOpt = record.borderId();
        if (borderIdOpt.isEmpty()) {
            return;
        }
        Optional<Border> borderOpt = BorderAPI.CRUD(level).flatMap(crud -> crud.get(borderIdOpt.get()));
        if (borderOpt.isEmpty()) {
            return;
        }
        if (borderOpt.get().radius() <= QUALIFYING_BORDER_RADIUS) {
            return;
        }

        cairnFixture(level).ifPresent(fixture ->
                fixture.createIfAbsent(record.bossId(), record.position()));
    }

    /** Public for {@code StructureCommands} -- same "standby, don't crash" resolver as
     * {@code BossAPI.bosses(Level)}. */
    public static Optional<CairnFixture> cairnFixture(Level level) {
        if (!Satchel.isReady()) {
            return Optional.empty();
        }
        LevelScope scope = new LevelScope(level);
        // FRO_150: Satchel.tryReadyScopeInfo() collapses the tryScopeInfo()/info.isReady() two
        // steps this used to hand-write into one call.
        var infoOpt = Satchel.tryReadyScopeInfo(FrontierKeys.STRUCTURE_JIG, scope);
        if (infoOpt.isEmpty()) {
            return Optional.empty();
        }
        var jig = (LevelJig) infoOpt.get().jigInfo().jig;
        StructureBundle bundle = jig.getOrCreate(scope, FrontierKeys.STRUCTURE_BUNDLE);
        return Optional.of(bundle.getOrCreateFixture(FrontierKeys.CAIRN, CairnFixture::new));
    }

    // ------------------------------------------------------------
    // Placement -- structure generation, no dependency on FRO_118 or SAT_072.
    // ------------------------------------------------------------

    private static void onTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        if (!FrontierKeys.STRUCTURE_JIG.equals(info.jigInfo().key)) {
            return;
        }
        if (!Satchel.isServer()) {
            return;
        }

        LevelScope scope = (LevelScope) info.scope();
        Level level = scope.level();

        var jig = (LevelJig) info.jigInfo().jig;
        StructureBundle bundle = jig.getOrCreate(scope, FrontierKeys.STRUCTURE_BUNDLE);
        CairnFixture fixture = bundle.getOrCreateFixture(FrontierKeys.CAIRN, CairnFixture::new);

        for (CairnRecord record : fixture.all()) {
            if (record.placed()) {
                continue;
            }
            attemptPlacement(level, fixture, record);
        }
    }

    private static void attemptPlacement(Level level, CairnFixture fixture, CairnRecord record) {
        UUID bossId = record.bossId();

        Optional<BossRecord> bossOpt = BossAPI.bosses(level).flatMap(f -> f.get(bossId));
        if (bossOpt.isEmpty()) {
            return;
        }
        Optional<Border> borderOpt = bossOpt.get().borderId()
                .flatMap(id -> BorderAPI.CRUD(level).flatMap(c -> c.get(id)));
        if (borderOpt.isEmpty()) {
            return;
        }

        BlockPos candidateXZ = fixture.pendingCandidate(bossId);
        if (candidateXZ == null) {
            int sampleRadius = Math.max(1,
                    (int) Math.round(borderOpt.get().radius() * PLACEMENT_RADIUS_FRACTION));
            candidateXZ = BorderMath.randomPointInDisk(RNG, bossOpt.get().position(), sampleRadius);
            fixture.setPendingCandidate(bossId, candidateXZ);
        }

        // Chunk-loaded check FIRST, before touching heightmap/block data -- same "no forced
        // load" discipline boss.md's own materialization check follows. Same candidate,
        // recheck next tick if not loaded yet (never a new random guess for this reason alone).
        if (!level.isLoaded(candidateXZ)) {
            return;
        }

        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidateXZ);

        boolean suitable = BossAPI.RULES(level)
                .map(r -> r.footprintSuitable(level, ground, PAD_SIZE, FOOTPRINT_CLEARANCE))
                .orElse(false);

        if (!suitable) {
            // Failed the footprint check outright (not just unloaded) -- resample a NEW point
            // next tick, per cairn.md's own "Placement" section.
            fixture.clearPendingCandidate(bossId);
            return;
        }

        buildStructure(level, ground);
        fixture.updateAnchor(bossId, ground);
        fixture.clearPendingCandidate(bossId);

        OUT.info("[Structure] Cairn placed for boss " + bossId + " at " + ground.getX() + ", "
                + ground.getY() + ", " + ground.getZ());
    }

    /**
     * Bug fix (playtest): this used to only place the pad's own floor blocks, never clear the
     * air above them. Every pad cell is built at the <em>center</em> candidate's resolved
     * ground height, but {@code footprintSuitable}'s own flatness tolerance allows real height
     * variation across the 3x3 area -- so on uneven terrain, a non-center cell's original,
     * taller ground can leave solid leftover blocks sitting right above the new pad surface.
     * That's exactly what a debug-teleported player standing on an edge cell suffocated in.
     * Matches the precedent {@code BossTellFixture.placePlatform} (Environmental Tells' own 3x3
     * platform) already established for the identical reason -- clear air above every cell, not
     * just the one the pillar sits on.
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
        level.setBlock(ground.above(PILLAR_HEIGHT), CAIRN_CAP.get().defaultBlockState(), 2);
    }

    // ------------------------------------------------------------
    // The pulse -- right-click and redstone both call this identical handler
    // (CairnCapBlock.use/neighborChanged).
    // ------------------------------------------------------------

    public static void triggerPulse(Level level, BlockPos capPos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Optional<CairnFixture> fixtureOpt = cairnFixture(level);
        if (fixtureOpt.isEmpty()) {
            return;
        }
        CairnFixture fixture = fixtureOpt.get();

        BlockPos anchorPos = capPos.below(PILLAR_HEIGHT);
        Optional<CairnRecord> recordOpt = fixture.getByAnchor(anchorPos);
        if (recordOpt.isEmpty()) {
            return;
        }
        UUID bossId = recordOpt.get().bossId();

        BlockPos target;
        if (BossAPI.isBossAlive(level, bossId)) {
            // "Just points to what it knows" -- overwrite the cache on a successful live read,
            // leave it untouched (and still use it) on failure. No fallback chain beyond this.
            Optional<BlockPos> liveOpt = BossAPI.resolveLivePosition(level, bossId);
            if (liveOpt.isPresent()) {
                fixture.updateLastKnownBossPos(bossId, liveOpt.get());
                target = liveOpt.get();
            } else {
                target = fixture.get(bossId).map(CairnRecord::lastKnownBossPos).orElse(anchorPos);
            }
        } else {
            // Dead path touches no boss state at all -- re-rolled every trigger, never cached.
            target = randomPointInSphere(RNG, anchorPos, DEAD_SPHERE_RADIUS);
        }

        firePulseParticles(serverLevel, capPos, target);
    }

    /**
     * Vertical launch (3-7 blocks) then a 3D bee-line (5-15 blocks) toward {@code target}'s
     * bearing -- both rolled fresh every trigger, per cairn.md's own visual spec. The raw
     * {@code target} coordinate itself never reaches the client -- only these already-resolved
     * particle points do, same secrecy rule as Boss's own position (boss.md § Module wiring).
     */
    private static void firePulseParticles(ServerLevel level, BlockPos capPos, BlockPos target) {
        double launchHeight = 3.0 + RNG.nextDouble() * 4.0;
        double beelineLength = 5.0 + RNG.nextDouble() * 10.0;

        double startX = capPos.getX() + 0.5;
        double startY = capPos.getY() + 1.0;
        double startZ = capPos.getZ() + 0.5;

        int launchSteps = (int) Math.ceil(launchHeight);
        for (int i = 1; i <= launchSteps; i++) {
            double y = startY + launchHeight * i / launchSteps;
            EffectsAPI.broadcastParticle(level, ParticleTypes.WITCH, startX, y, startZ, 1, 0, 0, 0, 0);
        }

        double apexY = startY + launchHeight;
        Vec3 dir = new Vec3(
                target.getX() + 0.5 - startX,
                target.getY() - apexY,
                target.getZ() + 0.5 - startZ
        ).normalize();

        int beelineSteps = (int) Math.ceil(beelineLength);
        for (int i = 1; i <= beelineSteps; i++) {
            double dist = beelineLength * i / beelineSteps;
            double x = startX + dir.x * dist;
            double y = apexY + dir.y * dist;
            double z = startZ + dir.z * dist;
            EffectsAPI.broadcastParticle(level, ParticleTypes.WITCH, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    /** Uniform random point within a sphere of {@code radius} centered on {@code center}. */
    @SuppressWarnings("SameParameterValue")
    private static BlockPos randomPointInSphere(RandomSource rng, BlockPos center, int radius) {
        double u = rng.nextDouble();
        double r = radius * Math.cbrt(u);
        double theta = rng.nextDouble() * 2.0 * Math.PI;
        double phi = Math.acos(2.0 * rng.nextDouble() - 1.0);

        double x = r * Math.sin(phi) * Math.cos(theta);
        double y = r * Math.sin(phi) * Math.sin(theta);
        double z = r * Math.cos(phi);

        return center.offset((int) Math.round(x), (int) Math.round(y), (int) Math.round(z));
    }

    // ------------------------------------------------------------
    // The capstone explosion -- SAT_072's BlockBreakSignal, now landed.
    // ------------------------------------------------------------

    /**
     * Non-cancelable in practice, per cairn.md's own "breaking is never prevented, only made
     * costly" ruling -- this never calls {@link BlockBreakSignal#cancel()}. Only the cap block
     * reacts; the base blocks and pad carry no reaction of their own by construction (they're
     * plain {@code Blocks.MOSSY_COBBLESTONE}, not this module's own block class).
     */
    private static void onBlockBreak(BlockBreakSignal signal) {
        if (!signal.state().is(CAIRN_CAP.get())) {
            return;
        }

        Level level = signal.level();
        BlockPos anchorPos = signal.pos().below(PILLAR_HEIGHT);

        // Extra "is this really a tracked Cairn" gate beyond the block-type check above --
        // defends against a stray world-placed cap (debug/creative give) triggering an
        // explosion with no real record behind it.
        boolean isTrackedCairn = cairnFixture(level)
                .flatMap(f -> f.getByAnchor(anchorPos))
                .isPresent();
        if (!isTrackedCairn) {
            return;
        }

        // Block-damage-free (ExplosionInteraction.NONE) -- punishes the break without griefing
        // anything around it. See CAPSTONE_EXPLOSION_POWER's own doc for the damage/knockback
        // tuning this power value controls.
        level.explode(null, signal.pos().getX() + 0.5, signal.pos().getY() + 0.5,
                signal.pos().getZ() + 0.5, CAPSTONE_EXPLOSION_POWER, Level.ExplosionInteraction.NONE);
    }
}
