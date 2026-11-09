package com.arryn.frontiermode.bosstell.common.fixture;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.BorderMath;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BorderCurve;
import com.arryn.frontiermode.border.common.fixture.BorderCurveFixture;
import com.arryn.frontiermode.border.common.fixture.Shape;
import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.bosstell.common.bundle.TellBundle;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import com.arryn.frontiermode.keeper.KeeperAPI;
import com.arryn.frontiermode.keeper.common.fixture.KeeperFixture;
import com.arryn.frontiermode.keeper.common.fixture.KeeperRecord;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.tunable.Tunable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Tell module's fixture, in {@link TellBundle} on {@code TELL_JIG} -- drives the per-boss
 * environmental tells (particle and sound), per
 * wiki/frontiermode/architecture/discovery-systems.md#environmental-tells. Tells only: the boss's
 * spawn platform is the Keeper's own ({@link com.arryn.frontiermode.keeper.common.BossPlatform}).
 *
 * <p><b>Tell pass (cadence-gated via {@link #tellTickInterval()}):</b>
 * for each server player on this level, finds the nearest alive+positioned boss with a
 * borderId, resolves its {@code "tell"}-purpose {@link BorderCurve} (LOG shape), computes
 * intensity from distance-to-boss (normalized by that boss' border's radius) via
 * {@link BorderMath#intensityAt(BorderCurve, double)}, then makes two <em>independent</em>
 * probability rolls: one for a particle spawn near the boss, one for an ambient sound
 * broadcast. Stateless: no attunement state, always "nearest right now" per player per
 * interval.
 *
     * <p>The fixture calls {@link BorderMath#intensityAt} directly rather than through
     * {@code BorderAPI.MATH} -- the two are equivalent.
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


    // Shared RNG -- one instance shared across every level's BossTellFixture. Not persisted; resets on server restart,
    // which is fine: we just skip one early-interval window.
    private static final RandomSource RNG = RandomSource.create();

    // round-robin ambient tell sounds,
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
    // SoundEvents.* constant is always safe eagerly; a custom RegistryObject is not.
    private static List<SoundEvent> tellSounds;

    private static List<SoundEvent> tellSounds() {
        if (tellSounds == null) {
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
    // each boss last actually played one (for the tellSoundCooldownTicks() gate below).
    // Deliberately NOT persisted -- same "off/on signal" ephemeral-state discipline as this
    // fixture's own tickCounter and WildsTellFixture's: resetting to slot 0 / no-cooldown on a
    // server restart is harmless, and neither map is large enough (one entry per boss that has
    // ever rolled a tell sound) to be a real memory concern left unbounded for a JVM's lifetime.
    private final Map<UUID, Integer> tellSoundRotation = new HashMap<>();
    private final Map<UUID, Long> lastTellSoundTick = new HashMap<>();

    // sound only fires within this fraction of the border radius from the boss. Playtest
    // tuning knob, not a mechanic -- safe baseline, adjust by ear.
    private static final double SOUND_MAX_NORMALIZED_DISTANCE = 0.3;
    // Inner radius (blocks) excluded around the player, so puffs never spawn on top of them.
    private static final int PARTICLE_INNER_SPREAD = 2;

    // Upper bound on independent scatter points (each its own small puff) spawned in one tell pass,
    // reached only at full intensity (standing on the boss) and full tellParticleCoefficient. The
    // actual count scales with intensity (see runTellsForPlayers), so the aura thickens as a player
    // closes in instead of firing one fixed group of bursts whenever a roll happens to pass.
    // Each puff is an independent point, jittered inside its block and spread wide, so the tell
    // reads as an aura rather than one blob. Tunable via tellParticleCoefficient; playtest
    // territory.
    private static final int PARTICLE_MAX_BURSTS = 24;
    // Radius (blocks) around the receiving player within which puffs are scattered -- kept inside
    // the ~32 blocks the client renders particles at, so every puff spawned can actually be seen.
    private static final int PARTICLE_PLAYER_RADIUS = 16;
    // How many particles spawn per burst point. Tunable; playtest territory.
    private static final int PARTICLE_COUNT = 8;
    // Per-particle offset (blocks) Minecraft scatters each burst's PARTICLE_COUNT within.
    // Horizontal is wide enough that a puff reads as a drift of smoke rather than one dot on a
    // block; vertical stays tighter so it hugs the ground.
    private static final double PARTICLE_OFFSET_XZ = 0.9;
    private static final double PARTICLE_OFFSET_Y = 0.4;

    // Non-persisted tell-pass cadence counter. Resets on server restart; the first partial
    // interval is silently skipped -- "off/on signal" discipline, no harm done.
    private int tickCounter = 0;

    /**
     * Spawns {@code bursts} small smoke bursts at independently randomized positions around
     * {@code center} (the receiving player), each after its own short heightmap ground check. Not
     * BorderPregen's full column check -- just a single heightmap lookup per burst, per the
     * discovery-systems spec ("short heightmap ground check").
     *
     * <p>Centered on the player: a boss-centered ring would scale with border size and put most
     * puffs out of the client's view. What tells the player they're near the boss
     * is therefore the <em>rate</em>: the per-player intensity roll upstream
     * (distance-to-boss) decides how many bursts fire, this method only places them. A fresh random
     * point per burst avoids the one-blob-relocating-each-interval look.
     */
    private static void spawnTellParticle(Level level, Player player, BlockPos center, int bursts) {
        for (int i = 0; i < bursts; i++) {
            // An area-uniform point in the ring [PARTICLE_INNER_SPREAD, PARTICLE_PLAYER_RADIUS] around
            // the PLAYER (center). A boss-centered ring scaled to the border would put nearly every
            // burst on a large border beyond the ~32 blocks the client renders. Centering on the
            // receiving player keeps every puff where they can see it; how MANY appear is still set
            // by their distance to the boss (see runTellsForPlayers), so the "getting warmer" signal
            // is the density, not the position. Drawn fresh every iteration so the bursts land
            // independently.
            double innerSq = (double) PARTICLE_INNER_SPREAD * PARTICLE_INNER_SPREAD;
            double outerSq = (double) PARTICLE_PLAYER_RADIUS * PARTICLE_PLAYER_RADIUS;
            double r = Math.sqrt(innerSq + (outerSq - innerSq) * RNG.nextDouble());
            double angle = RNG.nextDouble() * Math.PI * 2.0;
            BlockPos scatter = center.offset((int) Math.round(Math.cos(angle) * r), 0, (int) Math.round(Math.sin(angle) * r));
            BlockPos groundPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(scatter.getX(), 0, scatter.getZ()));
            // Targeted send (only this player sees it) -- unchanged from before; the fix here is
            // *where* it spawns, not who sees it. Routed through EffectsAPI rather than
            // calling the server's sendParticles directly.
            // jitter each puff inside its block (a bare +0.5 centred every burst on a block
            // centre, so repeated bursts piled onto the same spot) and use a wider horizontal offset.
            EffectsAPI.sendParticleToPlayer(player, ParticleTypes.SMOKE,
                    groundPos.getX() + RNG.nextDouble(), groundPos.getY() + 0.3, groundPos.getZ() + RNG.nextDouble(),
                    PARTICLE_COUNT, PARTICLE_OFFSET_XZ, PARTICLE_OFFSET_Y, PARTICLE_OFFSET_XZ, 0.01);
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    public void onJigTick() {
        Satchel.requireServer();
        TellBundle bundle = getBundle();
        @SuppressWarnings("resource") Level level = bundle.level();
        BossAPI.bosses(level).ifPresent(boss ->
                BorderAPI.CURVE(level).ifPresent(curves -> tick(level, boss, curves)));
    }

    // ------------------------------------------------------------------
    // the tell numbers, Tunable-backed and constructed inside their own accessors
    // ------------------------------------------------------------------

    /**
     * cadence for the tell-pass -- how many server ticks between each per-player intensity
     * evaluation and particle/sound roll. Safe baseline: 20 ticks (~1 second). Tuning is playtest territory.
     */
    private static int tellTickInterval() {
        return new Tunable("tellTickInterval", 1, 200, 20).intValue();
    }

    /**
     * probability coefficient for the tell particle roll, applied as intensity x this against a
     * [0,1) uniform random draw; intensity peaks at 1.0 right at the boss and fades to 0.0 at
     * the border edge. Playtest territory, tune further if it reads too sparse. [0,1] bound, this
     * coefficient is a probability roll.
     */
    private static double tellParticleCoefficient() {
        return new Tunable("tellParticleCoefficient", 0f, 1f, 0.7f).floatValue();
    }

    /**
     * probability coefficient for the tell sound roll. Independent of the particle roll -- same
     * intensity value, separate draw. 5% roll at maximum intensity: sounds rarer than particles by design. Safe
     * baseline; playtest territory.
     */
    private static double tellSoundCoefficient() {
        return new Tunable("tellSoundCoefficient", 0f, 1f, 0.05f).floatValue();
    }

    /**
     * hard per-boss cooldown (in ticks) on the tell
     * sound roll, on top of {@link #tellSoundCoefficient()}'s own probability. Needed because the
     * tell sound is a several-seconds-long clip: {@link #tellTickInterval()} (~1s) combined with a
     * 5% roll could easily re-trigger it -- by the same player rolling again, or by a different
     * player near the same boss rolling independently -- before the previous one finished,
     * stacking overlapping playback instead of a clean one-at-a-time rotation. This cooldown is
     * evaluated first: only once it's elapsed for a given boss does the probability roll even get
     * a chance to fire again for that boss. Safe baseline: 600 ticks (30 seconds), sized to the
     * clip length (round-robin, 4x ~30s clips) -- revisit per clip if the sounds aren't all the
     * same length.
     */
    private static int tellSoundCooldownTicks() {
        return new Tunable("tellSoundCooldownTicks", 0, 6000, 600).intValue();
    }

    private void tick(Level level, BossFixture bossFixture, BorderCurveFixture curveFixture) {
        Satchel.requireServer();

        // Tell pass is cadence-gated.
        tickCounter++;
        int interval = tellTickInterval();
        if (tickCounter < interval) {
            return;
        }
        tickCounter = 0;

        runTellsForPlayers(level, bossFixture, curveFixture);
    }

    // ------------------------------------------------------------------
    // Tell pass
    // ------------------------------------------------------------------

    private void runTellsForPlayers(Level level, BossFixture bossFixture, BorderCurveFixture curveFixture) {

        Optional<KeeperFixture> keepersOpt = KeeperAPI.keepers(level);
        if (keepersOpt.isEmpty()) {
            return;
        }
        KeeperFixture keepers = keepersOpt.get();

        // Alive+positioned bosses whose Keeper record names a border are the only candidates.
        List<BossRecord> candidates = bossFixture.all().stream()
                .filter(r -> r.alive() && r.positioned()
                        && keepers.get(r.bossId()).flatMap(KeeperRecord::borderId).isPresent())
                .toList();

        if (candidates.isEmpty()) {
            return;
        }

        var crudOpt = BorderAPI.CRUD(level);
        if (crudOpt.isEmpty()) {
            return;
        }
        var crud = crudOpt.get();

        double particleCoeff = tellParticleCoefficient();
        double soundCoeff = tellSoundCoefficient();

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

            UUID borderId = keepers.get(nearest.bossId()).flatMap(KeeperRecord::borderId)
                    .orElseThrow(); // present by construction of `candidates`

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
            // standing on the boss). Distance is measured to the boss' resolved position via the
            // lower-level, point-agnostic intensityAt(BorderCurve, double) overload, not to the
            // *border's geometric center*: boss placement is deliberately biased toward its border's
            // outer edge (so successive borders "crawl" outward), so a boss can easily sit at
            // ~80% of its own border's radius from center, and standing right on that boss' platform
            // would then read as ~0.15 intensity instead of ~1.0. Border radius still normalizes
            // the distance (there's no other natural "how far is far" reference).
            double normalizedDistance = BorderMath.distanceTo(nearest.position(), playerPos) / borderOpt.get().radius();
            double intensity = BorderMath.intensityAt(curveOpt.get(), normalizedDistance);
            if (intensity <= 0.0) {
                continue;
            }

            // Particles: the number of bursts this pass scales with intensity, so the aura is
            // sparse at the edge of the tell and dense on top of the boss (a single roll per pass
            // with a fixed burst count would be too rare and wouldn't thicken with closeness).
            // The fractional part of the expected count is rolled, so low intensity still fires
            // sometimes rather than rounding to nothing.
            double expectedBursts = intensity * particleCoeff * PARTICLE_MAX_BURSTS;
            int bursts = (int) expectedBursts;
            if (RNG.nextDouble() < expectedBursts - bursts) {
                bursts++;
            }
            if (bursts > 0) {
                spawnTellParticle(level, player, playerPos, bursts);
            }
            // The sound roll below is independent of the particles.
            // the sound plays at the player's own position, so distance only enters via
            // this trigger -- intensity > 0 across the whole border radius meant a layer 6 boss's
            // music fired from a huge distance. Gate it to the closer part of the border.
            if (normalizedDistance <= SOUND_MAX_NORMALIZED_DISTANCE
                    && RNG.nextDouble() < intensity * soundCoeff) {
                // Hard per-boss cooldown gates the probability roll above -- see
                // tellSoundCooldownTicks's own doc for why (~30s clips would otherwise stack/overlap).
                // Only once the cooldown has elapsed does this boss get to actually fire (and advance)
                // its round-robin.
                UUID tellBossId = nearest.bossId();
                long nowTick = level.getGameTime();
                long readyAtTick = lastTellSoundTick.getOrDefault(tellBossId, Long.MIN_VALUE)
                        + tellSoundCooldownTicks();

                if (nowTick >= readyAtTick) {
                    List<SoundEvent> tellSounds = tellSounds();
                    int soundIndex = tellSoundRotation.getOrDefault(tellBossId, 0);
                    SoundEvent tellSound = tellSounds.get(soundIndex);

                    // Broadcast to all nearby players (area broadcast, per discovery-systems
                    // spec), routed through EffectsAPI. Volume intentionally low; real tuning is playtest
                    // territory.
                    EffectsAPI.broadcastSound(level, player.getX(), player.getY(), player.getZ(), tellSound, SoundSource.AMBIENT, 0.5f, 0.8f + RNG.nextFloat() * 0.4f);

                    lastTellSoundTick.put(tellBossId, nowTick);
                    tellSoundRotation.put(tellBossId, (soundIndex + 1) % tellSounds.size());
                }
            }
        }
    }
}
