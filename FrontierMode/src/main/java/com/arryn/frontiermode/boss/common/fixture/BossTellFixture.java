package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.BorderMath;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BorderCurve;
import com.arryn.frontiermode.border.common.fixture.BorderCurveFixture;
import com.arryn.frontiermode.border.common.fixture.Shape;
import com.arryn.frontiermode.boss.common.bundle.BossBundle;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
 *       intensity from distance-to-boss (normalized by that boss' border's radius) via
 *       {@link BorderMath#intensityAt(BorderCurve, double)}, then makes two <em>independent</em>
 *       probability rolls: one for a particle spawn near the boss, one for an ambient sound
 *       broadcast. Stateless: no attunement state, always "nearest right now" per player per
 *       interval.
 * </ol>
 *
 * <p>The fixture calls {@link BorderMath#intensityAt} directly rather than through
 * {@code BorderAPI.MATH} -- both are equivalent now that FRO_088 fixed {@code BorderAPI.MATH}'s
 * uninitialized field, but the direct call predates that fix and was left as-is.
 */
public final class BossTellFixture extends SatchelFixture {

    /**
     * Creates a {@code "tell"}-purpose {@link BorderCurve} (LOG shape, steepness 1.0) for
     * {@code borderId} if none exists yet. Idempotent -- a no-op after a server restart.
     * Called at every paired boss-creation call site (bootstrap, real-death cascade,
     * {@code /boss transform defeat}, {@code /boss attach}) that has a real border to key off.
     */
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

    // RM_FRO_037 (Curtis, boss-audio-tell follow-up, 2026-09-08): round-robin ambient tell sounds,
    // per-boss (not one shared global rotation across every boss -- project owner's explicit
    // call), so two different bosses can be on different tracks at once. All 5 slots are now real
    // project-owner-supplied clips (FrontierMode.BOSS_TELL_1..5) -- 1 was converted here via
    // ffmpeg from a stereo source; 2-5 arrived as ready-to-use mono OGG Vorbis and were copied in
    // byte-for-byte with no re-encoding (bitrate/sample rate deliberately left unstandardized
    // across the 5, per project owner -- Minecraft's sound engine doesn't care).
    //
    // Lazily initialized rather than an eager static final field: mixing a mod-registered
    // RegistryObject into a field evaluated at class-load time risks the same class of bug
    // Rendering.java's own bordersRenderer() doc already documents in this codebase (an eager
    // static initializer touching something not guaranteed ready yet -- there it was dist-loading
    // order crashing a dedicated server's first tick; here it would be FrontierMode.BOSS_TELL_n
    // .get() potentially running before Forge's registry event populates it). A vanilla
    // SoundEvents.* constant (this fixture's original placeholder, before any real asset existed)
    // is always safe eagerly; a custom RegistryObject is not.
    private static List<SoundEvent> tellSounds;

    private static List<SoundEvent> tellSounds() {
        if (tellSounds == null) {
            // 2026-09-08: all 5 real now -- project owner supplied slots 2-5 as ready-to-use mono
            // OGG Vorbis directly (copied byte-for-byte into resources, no re-encoding), following
            // slot 1's own real asset from the prior entry in this ticket's log.
            tellSounds = List.of(
                    FrontierMode.BOSS_TELL_1.get(),
                    FrontierMode.BOSS_TELL_2.get(),
                    FrontierMode.BOSS_TELL_3.get(),
                    FrontierMode.BOSS_TELL_4.get(),
                    FrontierMode.BOSS_TELL_5.get()
            );
        }
        return tellSounds;
    }

    // Per-boss round-robin index into TELL_SOUNDS (which slot plays next) and the game-time tick
    // each boss last actually played one (for the BossRules#tellSoundCooldownTicks() gate below).
    // Deliberately NOT persisted -- same "off/on signal" ephemeral-state discipline as this
    // fixture's own tickCounter and ExteriorTellFixture's: resetting to slot 0 / no-cooldown on a
    // server restart is harmless, and neither map is large enough (one entry per boss that has
    // ever rolled a tell sound) to be a real memory concern left unbounded for a JVM's lifetime.
    private final Map<UUID, Integer> tellSoundRotation = new HashMap<>();
    private final Map<UUID, Long> lastTellSoundTick = new HashMap<>();

    // Outer radius (blocks) of the ring particles scatter across around the boss' platform, as a
    // fraction of *that boss' own border's radius* -- not a fixed block count. Player call,
    // 2026-09-06: "base it on 30% of the last path size, then it grows with the game." A fixed
    // radius either felt tiny once borders grow large, or oversized on the very first, smallest
    // border -- scaling with the border it belongs to means an early boss (BorderConstants
    // .DEFAULT_RADIUS=16 -> ~5 blocks) and a late-game boss (borders grow 1.5x per layer, capped
    // at BorderConstants.MAX_RADIUS=512 -> ~154 blocks) both get a tell radius proportionate to
    // that border's own scale. Computed per-tell in runTellsForPlayers from the resolved
    // Border.radius(), not a static field. Tunable; playtest territory.
    private static final double PARTICLE_SPREAD_FRACTION = 0.3;
    // Inner radius (blocks) excluded from that ring -- keeps particles off the 3x3 platform
    // itself, so they read as an aura *around* it rather than occasionally landing on its
    // surface. Fixed, not border-scaled -- the platform itself is always 3x3 regardless of border
    // size. Player suggestion, 2026-09-06 ("a non-path border around the boss location that just
    // adds the particles"): this reuses BorderMath's existing disk/annulus sampling
    // (randomPointInAnnulus is already a pure center+radius function, no Border object required)
    // rather than a hand-rolled square offset or an actual second Border entity -- same circular
    // "area around a point" the suggestion was after, without a new border to create and manage
    // the lifecycle of.
    private static final int PARTICLE_INNER_SPREAD = 2;

    // How many independent scatter points get their own particle burst per tell. Bug found
    // 2026-09-06 ("are they stacking on top of each other at one coordinate? ... only
    // concentrated on a single spot"): spawnTellParticle used to draw exactly ONE random point
    // from the annulus and dump every particle there with only a small jitter box -- so instead
    // of a field of tells scattered across the boss' aura, every successful roll looked like one
    // dense blob that merely teleports to a new random spot each tick-interval. Fixed by drawing
    // PARTICLE_BURSTS independent points from the same annulus each tell, each getting its own
    // (smaller) particle count -- an actual scattered ring around the boss instead of one point.
    // Confirmed via playtest ("that's about where I want it") -- kept at 6 as the real baseline,
    // not just a debug value; it's what gives the ring its shape.
    private static final int PARTICLE_BURSTS = 6;
    // How many particles spawn per burst point (so total visible particles per tell is roughly
    // PARTICLE_BURSTS x this). Was temporarily 50 (~300 total) purely as a location-debug aid,
    // per the player's "make it a ridiculous amount to see where they're actually showing up" --
    // once the scatter shape was confirmed good, dialed back to 8 (~48 total across 6 points),
    // close to the original single-point 24 baseline's order of magnitude but now actually
    // spread across the ring instead of piled in one spot. Tunable; playtest territory.
    private static final int PARTICLE_COUNT = 8;
    // Per-particle offset box (blocks) Minecraft scatters each burst's PARTICLE_COUNT within --
    // keeps each burst reading as a small puff rather than a single dense point, without being
    // wide enough to blur separate bursts together.
    private static final double PARTICLE_OFFSET = 0.45;

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
     * Spawns {@value #PARTICLE_BURSTS} small particle bursts at independently randomized
     * positions around {@code bossPos} (the boss' actual position -- same point
     * {@link #placePlatform} builds the 3x3 platform under), each after its own short heightmap
     * ground check. Not BorderPregen's full column check -- just a single heightmap lookup per
     * burst, per the discovery-systems spec ("short heightmap ground check").
     *
     * <p>Playtest feedback, 2026-09-06: this originally centered on the <em>player's</em>
     * position, which read as "particles following you around" rather than an environmental tell
     * at the boss itself -- moved to center on the boss' platform instead. Bug found the same
     * day ("stacking on top of each other at one coordinate... concentrated on a single spot"):
     * a single call used to draw one random point and dump every particle there, so a tell read
     * as one blob relocating each tick rather than a scattered field -- fixed by drawing a fresh
     * random point per burst instead of one point for all of them. The per-player intensity roll
     * upstream (distance-to-boss) still gates whether/how often this fires at all; this method
     * only controls the shape of what appears once it does.
     */
    private static void spawnTellParticle(Level level, ServerPlayer player, BlockPos bossPos, int scatterRadius) {
        for (int i = 0; i < PARTICLE_BURSTS; i++) {
            // Area-uniform point in the ring [PARTICLE_INNER_SPREAD, scatterRadius] around the
            // boss -- same BorderMath primitive DefaultBossRules.choosePosition uses for placing
            // a new border's boss away from its own border's center (FRO_072), reused here for
            // the same "point somewhere in an annulus around this center" shape. scatterRadius is
            // caller-computed from this boss' own border's radius (see PARTICLE_SPREAD_FRACTION's
            // doc) and already guaranteed >= PARTICLE_INNER_SPREAD. Drawn fresh every iteration
            // so the PARTICLE_BURSTS points land independently across the ring.
            BlockPos scatter = BorderMath.randomPointInAnnulus(RNG, bossPos, PARTICLE_INNER_SPREAD, scatterRadius);
            BlockPos groundPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(scatter.getX(), 0, scatter.getZ()));
            // Targeted send (only this player sees it) -- unchanged from before; the fix here is
            // *where* it spawns, not who sees it. Routed through EffectsAPI (FRO_089) rather than
            // calling ServerLevel#sendParticles directly.
            EffectsAPI.sendParticleToPlayer(player, ParticleTypes.SMOKE, groundPos.getX() + 0.5, groundPos.getY() + 0.5, groundPos.getZ() + 0.5, PARTICLE_COUNT, PARTICLE_OFFSET, PARTICLE_OFFSET, PARTICLE_OFFSET, 0.01);
        }
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
            OUT.warn("[BossTell] createRecord(): record already exists for bossId=" + Ids.shortId(bossId) + " -- ignoring.");
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
            OUT.warn("[BossTell] setPlatformPos(): no tell record for bossId=" + Ids.shortId(bossId) + " -- ignoring.");
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

        // Tell pass needs a ServerLevel (EffectsAPI's targeted-particle path needs ServerPlayer,
        // which only comes off a real ServerLevel's player list) -- same warn-and-return
        // discipline as this fixture's other defensive guards (e.g. setPlatformPos).
        if (!(level instanceof ServerLevel serverLevel)) {
            OUT.warn("[BossTell] tick(): level is not a ServerLevel -- skipping tell pass.");
            return;
        }
        runTellsForPlayers(serverLevel, bossFixture, curveFixture);
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
            OUT.info("[BossTell] Built 3×3 bedrock platform for bossId=" + Ids.shortId(tellRecord.bossId()) + " centered at " + bossPos.below());
        }
    }

    private void runTellsForPlayers(ServerLevel level, BossFixture bossFixture, BorderCurveFixture curveFixture) {

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

        for (ServerPlayer player : level.players()) {
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

            // Intensity: 0.0 (player a full border-radius away from the boss) -> 1.0 (player
            // standing on the boss). Bug found 2026-09-06 ("there's none showing at all now"):
            // this used to call BorderMath.intensityAt(Border, BorderCurve, BlockPos), which
            // measures distance to the *border's geometric center* -- not the boss. That's a
            // real mismatch: FRO_072 deliberately biases boss placement toward its border's
            // outer edge (so successive borders "crawl" outward), so a boss can easily sit at
            // ~80% of its own border's radius from center. Standing right on that boss' platform
            // then read as ~0.15 intensity instead of ~1.0 -- particles could go missing for
            // whole play sessions by chance alone. Fixed to measure distance to the boss'
            // resolved position instead, via the lower-level, point-agnostic
            // intensityAt(BorderCurve, double) overload -- border radius is still what normalizes
            // the distance (there's no other natural "how far is far" reference), just no longer
            // centered on the wrong point.
            double normalizedDistance = BorderMath.distanceTo(nearest.position(), playerPos) / borderOpt.get().radius();
            double intensity = BorderMath.intensityAt(curveOpt.get(), normalizedDistance);
            if (intensity <= 0.0) {
                continue;
            }

            // Two independent probability rolls, one coefficient each.
            if (RNG.nextDouble() < intensity * particleCoeff) {
                // Scale the scatter radius off this boss' own border, not a fixed constant --
                // clamped to at least PARTICLE_INNER_SPREAD so the annulus stays valid even for
                // BorderConstants.MIN_RADIUS-sized borders.
                int scatterRadius = Math.max(PARTICLE_INNER_SPREAD,
                        (int) Math.round(borderOpt.get().radius() * PARTICLE_SPREAD_FRACTION));
                spawnTellParticle(level, player, nearest.position(), scatterRadius);
            }
            if (RNG.nextDouble() < intensity * soundCoeff) {
                // RM_FRO_037 follow-up (2026-09-08): hard per-boss cooldown gates the probability
                // roll above -- see BossRules#tellSoundCooldownTicks's own doc for why (4x ~30s
                // clips would otherwise stack/overlap at this roll's original sub-second-blip
                // cadence). Only once the cooldown has elapsed does this boss get to actually
                // fire (and advance) its round-robin.
                UUID tellBossId = nearest.bossId();
                long nowTick = level.getGameTime();
                long readyAtTick = lastTellSoundTick.getOrDefault(tellBossId, Long.MIN_VALUE)
                        + bossFixture.RULES.tellSoundCooldownTicks();

                if (nowTick >= readyAtTick) {
                    List<SoundEvent> tellSounds = tellSounds();
                    int soundIndex = tellSoundRotation.getOrDefault(tellBossId, 0);
                    SoundEvent tellSound = tellSounds.get(soundIndex);

                    // Broadcast to all nearby players (area broadcast, per discovery-systems
                    // spec), routed through EffectsAPI (FRO_089). Volume intentionally low; slot 1
                    // is a real project-owner-supplied clip now, slots 2-5 are still the
                    // AMBIENT_CAVE placeholder -- real tuning is playtest territory.
                    EffectsAPI.broadcastSound(level, player.getX(), player.getY(), player.getZ(), tellSound, SoundSource.AMBIENT, 0.5f, 0.8f + RNG.nextFloat() * 0.4f);

                    lastTellSoundTick.put(tellBossId, nowTick);
                    tellSoundRotation.put(tellBossId, (soundIndex + 1) % tellSounds.size());
                }
            }
        }
    }
}
