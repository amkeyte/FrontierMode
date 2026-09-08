package com.arryn.frontiermode.border.common.fixture;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.common.bundle.BordersBundle;
import com.arryn.frontiermode.border.common.BorderPregenLogic;
import com.arryn.frontiermode.border.server.rules.BorderRules;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.common.util.throttle.TickThrottler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
//import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Fourth sibling fixture to {@link BordersFixture} inside
 * {@link com.arryn.frontiermode.border.common.bundle.BordersBundle} -- proactively,
 * throttle-paced generates a border's entire disk once explicitly triggered to, per RM_FRO_028
 * ("Diane") / wiki/frontiermode/architecture/border-pregeneration.md. Registered alongside its
 * siblings in {@code BorderModule.init()}.
 *
 * <p><b>Pregeneration is an explicit action, never automatic on border creation</b> -- see that
 * page's "Starting a border's pregeneration is an explicit call" section. No record for a given
 * border id at all means "never triggered," a real third state alongside "in progress"
 * ({@link BorderPregenRecord#complete()} {@code == false}) and "complete."
 *
 * <p>Tick-driven work runs through {@link #onJigTick()} -- fixture-level, automatically invoked
 * once per valid tick for this fixture's own bundle instance (Satchel's {@code TrackerFixture} is
 * the precedent for this hook), so no separate {@code EventHandlers} wiring is needed in
 * {@code BorderModule.init()} the way {@code Rendering} need for
 * {@link BordersFixture}'s own tick-driven neighbors. Reuses
 * {@code com.arryn.satchel.common.util.throttle.TickThrottler} for pacing, per that page's
 * "Reused, not reinvented" section -- the same class {@link SatchelFixture}'s own trace-log
 * rate-limiting already uses, general-purpose rather than log-specific.
 */
public final class BorderPregenFixture extends SatchelFixture {

    private static final String KEY_JOBS = "pregenJobs";

    // Exact throttle budget lives on BorderRules/DefaultBorderRules (pregenChunksPerBatch() /
    // pregenThrottleIntervalTicks()) as a Lead Dev-tunable coefficient, per
    // wiki/frontiermode/architecture/border-pregeneration.md's "Open questions" / FRO_092 -- not
    // a hardcoded constant here. See DefaultBorderRules for the current values and the playtest
    // notes behind them.

    // Liveness check (FRO_092): fires every STALL_CHECK_INTERVAL_TICKS and crashes if an
    // in-progress job's cursor hasn't moved since the last check -- a border-creation call site
    // that forgot to trigger pregeneration, or any other reason a job stops making progress,
    // surfaces immediately and loudly instead of stalling silently forever. Deliberately
    // disk-size-agnostic: "still ongoing" is relative to this job's own last checkpoint, not a
    // predicted total duration.
    private static final long STALL_CHECK_INTERVAL_TICKS = 200L;

    // A grown border's disk is concentric with its predecessor's -- same center, bigger radius
    // (see BordersPathFacet.grow()/DefaultBorderRules' "reuses the previous center" comment) --
    // so a large inner prefix of a new layer's offset list was very likely already forced to
    // FULL by the previous layer's own completed job. runBatch() prechecks each offset with a
    // non-forcing getChunk(status, false) status lookup before paying for real generation; an
    // already-FULL chunk is skipped without spending the batch's chunk budget. That lookup is
    // far cheaper than generation but not literally free, and an overlap run can span hundreds
    // of chunks -- this caps how many offsets get *examined* (skip or generate) in one batch, so
    // a long all-already-generated run can't become its own "Can't keep up" case the way
    // unthrottled real generation did.
    private static final int MAX_OFFSETS_SCANNED_PER_BATCH = 64;

    private final List<BorderPregenRecord> jobs = new ArrayList<>();
    private final TickThrottler throttler =
            new TickThrottler(BorderRules.ACTIVE.pregenThrottleIntervalTicks(), new TickThrottler.AutoClock());

    private final TickThrottler stallCheckThrottler =
            new TickThrottler(STALL_CHECK_INTERVAL_TICKS, new TickThrottler.AutoClock());

    // Wall-clock start times for the "how long did this take" log line below -- deliberately not
    // persisted (just a log nicety, not state anything reads back): a job resumed after a server
    // restart just logs its completion without a duration instead of a wrong one.
    private final Map<UUID, Long> startedAtMillis = new HashMap<>();

    // Liveness bookkeeping for the stall check above -- deliberately not persisted, same as
    // startedAtMillis: a server restart just starts watching fresh rather than falsely flagging
    // a resumed job as stalled on its very first tick back.
    private final Map<UUID, Integer> lastSeenCursor = new HashMap<>();

    public BorderPregenFixture() {
        registerCustom(KEY_JOBS, this::saveJobs, this::loadJobs);
    }

    private void saveJobs(CompoundTag root) {
        Satchel.requireServer();

        ListTag list = new ListTag();
        for (BorderPregenRecord record : jobs) {
            list.add(BorderPregenRecord.save(record));
        }
        root.put(KEY_JOBS, list);
    }

    private void loadJobs(CompoundTag root) {
        if (!root.contains(KEY_JOBS, Tag.TAG_LIST)) {
            return;
        }
        jobs.clear();

        ListTag list = root.getList(KEY_JOBS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            // Same discipline BossFixture.loadBosses/BordersFixture.loadBorders follow: one
            // malformed record shouldn't abort hydration of every other job on this level.
            try {
                jobs.add(BorderPregenRecord.load((CompoundTag) t));
            } catch (RuntimeException e) {
                OUT.warn("[BorderPregen] Skipping malformed pregen record during load: " + e);
            }
        }
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    private Optional<BorderPregenRecord> get(UUID borderId) {
        return jobs.stream().filter(r -> r.borderId().equals(borderId)).findFirst();
    }

    /**
     * Per-border readiness. Per this fixture's own class doc, {@code false} covers three
     * different real states at once -- "never triggered," "in progress," and "this fixture
     * itself isn't ready to answer yet" (check {@link #isReady()} first for that last one, the
     * base, no-argument, whole-fixture question every {@link SatchelFixture} answers the same
     * way) -- a caller doesn't need to and can't distinguish which from this method alone,
     * matching {@code BorderAPI.isPregenReady()}'s own contract.
     */
    public boolean isReadyFor(UUID borderId) {
        return get(borderId).map(BorderPregenRecord::complete).orElse(false);
    }

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------

    /**
     * Starts a border's pregeneration job. A no-op (returns {@code false}) if a job already
     * exists for {@code borderId}, whether still in progress or already complete -- calling this
     * twice is safe, not an error, per this fixture's own class doc.
     */
    public boolean start(UUID borderId) {
        Satchel.requireServer();
        Objects.requireNonNull(borderId, "borderId");

        if (get(borderId).isPresent()) {
            return false;
        }

        jobs.add(new BorderPregenRecord(borderId, false, 0));
        startedAtMillis.put(borderId, System.currentTimeMillis());
        markDirty();
        OUT.info("[BorderPregen] Pregeneration started for border " + Ids.shortId(borderId) + ".");
        return true;
    }

    private void advance(UUID borderId, int newCursor, int totalChunks) {
        Optional<BorderPregenRecord> existing = get(borderId);
        if (existing.isEmpty()) {
            return;
        }

        BorderPregenRecord updated = existing.get().advancedTo(newCursor, totalChunks);
        jobs.removeIf(r -> r.borderId().equals(borderId));
        jobs.add(updated);
        markDirty();

        // Edge-triggered: only on the false -> true transition, whether it completed normally,
        // the border was deleted mid-job, or it shrank past its own cursor (runBatch()'s own two
        // early-out cases above both force completion the same way) -- any of the three really is
        // "this job has stopped running," which is what this log line is for.
        if (updated.complete() && !existing.get().complete()) {
            Long startedAt = startedAtMillis.remove(borderId);
            String durationText = startedAt != null
                    ? String.format(" (took %.1fs)", (System.currentTimeMillis() - startedAt) / 1000.0)
                    : "";
            OUT.info("[BorderPregen] Pregeneration complete for border " + Ids.shortId(borderId) + durationText + ".");
        }
    }

    // ------------------------------------------------------------------
    // Tick-driven work
    // ------------------------------------------------------------------

    @Override
    public void onJigTick() {
        if (!isReady()) {
            return;
        }

        if(!Satchel.isServer()){
            return;
        }

        checkStalled();

        if (!throttler.allow()) {
            return;
        }

        // Defensive copy -- runBatch()'s own advance() call mutates `jobs` in place per record,
        // same "don't iterate the live backing list while mutating it" discipline
        // BossFixture.all()'s own FRO_060 fix documents.
        for (BorderPregenRecord record : List.copyOf(jobs)) {
            if (record.complete()) {
                continue;
            }
            runBatch(((LevelScope) scope()).level(), record);
        }
    }

    /**
     * Liveness check, per this class's own "Liveness check" doc above -- crashes if an
     * in-progress job's cursor hasn't moved since the last time this check ran for it. Runs
     * ahead of the pacing throttler's own gate so it keeps watching even on ticks the pacing
     * throttler itself declines.
     */
    private void checkStalled() {
        if (!stallCheckThrottler.allow()) {
            return;
        }

        for (BorderPregenRecord record : List.copyOf(jobs)) {
            if (record.complete()) {
                lastSeenCursor.remove(record.borderId());
                continue;
            }

            Integer previousCursor = lastSeenCursor.put(record.borderId(), record.cursor());
            if (previousCursor != null && previousCursor == record.cursor()) {
                throw new IllegalStateException(
                        "[BorderPregen] Border " + record.borderId()
                                + " appears stalled -- cursor stuck at " + record.cursor()
                                + " for at least " + STALL_CHECK_INTERVAL_TICKS + " ticks.");
            }
        }
    }

    private void runBatch(Level level, BorderPregenRecord record) {
        // FRO_077: direct sibling access within this fixture's own BordersBundle, not the
        // BorderAPI.CRUD() facade -- the facade exists for external callers, not internal bundle
        // communication (see BordersBundle -- this fixture is a sibling of BordersFixture in the
        // same bundle instance, reached here via getBundle() rather than a fresh level lookup).
        Optional<Border> borderOpt = this.<BordersBundle>getBundle()
                .get(FrontierKeys.BORDERS)
                .flatMap(fixture -> fixture.CRUD.get(record.borderId()));
        if (borderOpt.isEmpty()) {
            // The border was deleted mid-pregeneration. BorderCurve's own page requires an
            // explicit delete-cascade for its own records (see BorderAPI.removeBorder); this
            // page makes no equivalent requirement for pregen jobs. There is nothing left to
            // generate for an id that no longer resolves to anything, so marking the job complete
            // (rather than leaving it to retry forever) stops it silently spinning every
            // throttled tick for the rest of the level's life.
            advance(record.borderId(), record.cursor(), record.cursor());
            return;
        }
        Border border = borderOpt.get();

        // Recomputed fresh from the border's *current* radius every batch, not cached at job
        // start -- if the border grows mid-pregen (/border transform), the newly-uncovered ring
        // is picked up automatically on the next batch rather than being silently missed.
        int chunkRadius = BorderPregenLogic.chunkRadiusFor(border.radius());
        List<int[]> offsets = BorderPregenLogic.chunkOffsetsInDisk(chunkRadius);
        int total = offsets.size();

        // cursor() == 0 only ever true on this job's very first batch (advance() only moves it
        // forward) -- logs the disk size exactly once per job, right after "started", instead of
        // making someone count individual chunk-load calls.
        if (record.cursor() == 0) {
            OUT.info("[BorderPregen] Border " + Ids.shortId(record.borderId()) + " disk is " + total
                    + " chunks (radius " + chunkRadius + ").");
        }

        if (record.cursor() >= total) {
            // Defensive: a border that *shrank* mid-pregen could already be past its own
            // (smaller) new total. Same "mark complete, stop respinning" outcome as the
            // border-deleted case above.
            advance(record.borderId(), record.cursor(), total);
            return;
        }

        int centerChunkX = border.center().getX() >> 4;
        int centerChunkZ = border.center().getZ() >> 4;

        // Two independent caps: `generated` bounds real generation calls (the throttled-cost
        // work chunksPerBatch was always meant to pace), `scanned` bounds total offsets looked
        // at either way (the MAX_OFFSETS_SCANNED_PER_BATCH safety valve above). Cursor still
        // just indexes the same deterministic offset list either way -- resumability is
        // unaffected by how many of a given batch turned out to be skips.
        int chunksPerBatch = BorderRules.ACTIVE.pregenChunksPerBatch();
        int generated = 0;
        int scanned = 0;
        int i = record.cursor();
        while (i < total && generated < chunksPerBatch && scanned < MAX_OFFSETS_SCANNED_PER_BATCH) {
            int[] offset = offsets.get(i);
            int chunkX = centerChunkX + offset[0];
            int chunkZ = centerChunkZ + offset[1];

            // require=false: returns the chunk only if it's already at/past FULL (loaded, or on
            // disk with no generation work needed) -- null if it would have to actually generate.
            // Costs a status lookup, not generation, so this doesn't touch chunksPerBatch.
            ChunkAccess existing =
                    level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            if (existing == null) {
                // Not already there -- the real, forcing call, same as this loop always made.
                level.getChunk(chunkX, chunkZ);

                // FRO_092: Border only promises generation, never suitability (see
                // wiki/frontiermode/architecture/border-pregeneration.md) -- but generation
                // itself is still expected to succeed every time it's forced. Vanishingly
                // unlikely per FRO_091's own ruling, and not worth a retry/reroll mechanism
                // against a failure mode nobody's actually hit -- crash loudly instead so a
                // pathological seed or extreme biome surfaces immediately as a bug report, not a
                // silently incomplete disk.
                if (level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) {
                    throw new IllegalStateException(
                            "[BorderPregen] Border " + record.borderId()
                                    + " chunk (" + chunkX + ", " + chunkZ + ") at cursor " + i
                                    + " did not reach FULL status after forced generation.");
                }

                generated++;
            }
            scanned++;
            i++;
        }

        advance(record.borderId(), i, total);
    }
}
