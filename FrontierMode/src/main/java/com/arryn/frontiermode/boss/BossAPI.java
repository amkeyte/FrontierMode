package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.Result;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.jig.guts.SatchelException;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * Side-agnostic ingress API for interacting with Bosses -- mirrors {@code BorderAPI}'s shape and
 * "standby, don't crash" discipline exactly.
 */
public final class BossAPI {

    private BossAPI() {
    }

    private static LogicalFoundation foundation() {
        return Satchel.require();
    }

    private static LevelJig levelJig() {
        return (LevelJig)
                foundation().requireJigInfo(FrontierKeys.BOSS_JIG).jig;
    }

    public static Optional<BossFixture> bosses(Level level) {
        if (!Satchel.isReady()) {
            OUT.debug("[BossAPI] bosses(): Satchel not ready yet -> Optional.empty level="
                    + level.dimension().location());
            return Optional.empty();
        }

        LevelScope scope = new LevelScope(level);

        var infoOpt = Satchel.require().tryScopeInfo(FrontierKeys.BOSS_JIG, scope);
        if (infoOpt.isEmpty()) {
            OUT.debug("[BossAPI] bosses(): scope not yet known -> Optional.empty level="
                    + level.dimension().location());
            return Optional.empty();
        }

        var info = infoOpt.get();
        if (!info.isReady()) {
            OUT.debug("[BossAPI] bosses(): scope NOT ready -> Optional.empty level="
                    + level.dimension().location() + " phase=" + info.phase());
            return Optional.empty();
        }

        try {
            return levelJig()
                    .getOrCreate(scope, FrontierKeys.BOSS_BUNDLE)
                    .get(FrontierKeys.BOSS);
        } catch (RuntimeException e) {
            throw new SatchelException.AccessFailed(
                    "Failed to resolve BossFixture for level " + level.dimension().location(), e);
        }
    }

    /**
     * Creates a new, unpositioned boss record for {@code border} -- the direct call paired at a
     * real border-creation call site (see boss.md's "Defeat detection and the border-growth gap").
     * Border Pregeneration moved position selection off this call entirely: the record starts
     * with a null position, and {@code BossModule}'s own tick finalizes a real one only once
     * {@code BorderAPI.isPregenReady()} passes for {@code border} -- see
     * wiki/frontiermode/architecture/border-pregeneration.md#what-this-changes-in-boss.
     * {@code layer} is copied once from {@code border.layer()} and never re-read from
     * {@code border} afterward.
     */
    public static Optional<BossRecord> createBoss(Level level, Border border) {
        Optional<BossFixture> fixtureOpt = bosses(level);
        if (fixtureOpt.isEmpty()) {
            OUT.warn("[Boss] createBoss(): BossFixture not available for level "
                    + level.dimension().location() + " -- border " + border.id() + " gets no boss record.");
            return Optional.empty();
        }

        // FRO_058: BossFixture.create() now returns Optional<BossRecord> itself (empty on a
        // rejected negative layer) -- no wrapping needed here anymore.
        return fixtureOpt.get().create(border.layer());
    }

    /**
     * Bundles {@link #forceDefeat}'s outcome -- {@code borderResult} is exactly what
     * {@code BorderAPI.grow} returned (its own {@code border()} is the newly grown
     * <em>border</em>, not a boss), and {@code nextBoss} is the actual {@link BossRecord}
     * {@link #createBoss} produced for it, if any. FRO_057 playtest turned up that reporting
     * {@code borderResult.border().id()} as "the next boss" was actively wrong -- a border id
     * and a boss id are different UUIDs, and the chat feedback was showing the former labeled as
     * the latter.
     */
    public record DefeatOutcome(Result borderResult, Optional<BossRecord> nextBoss) {
    }

    /**
     * Force-defeats a boss record without combat, then runs the same cascade a real death does
     * ({@code BorderAPI.grow} centered on the record's own stored position, then
     * {@link #createBoss} for the resulting border) -- FRO_057's {@code /boss transform defeat},
     * built to reproduce the full "boss defeated -> border grows -> next boss queued" effect on
     * demand, not just flip the record's {@code alive} flag. Mirrors {@code BossModule
     * .onLivingDeath}'s own cascade shape, but keyed off a selector-resolved {@code bossId}
     * instead of a dying {@code Mob}, and centered on the record's stored {@code position()}
     * rather than a live entity's block position -- deliberately not refactored to share code
     * with {@code onLivingDeath} itself, which is Karen's own already playtest-verified path and
     * out of this ticket's scope to touch.
     *
     * @return a {@link DefeatOutcome} -- {@code borderResult} is {@code notFound} if no record
     *         matches {@code bossId}, {@code notReady} if the level's Boss/Border data isn't
     *         resolvable yet, {@code validationRejected} if the record is already defeated
     *         (FRO_058's guard -- see {@code BossFixture.markDefeated}), otherwise whatever
     *         {@code grow} itself returns; {@code nextBoss} is empty whenever {@code borderResult}
     *         isn't a success, and also (rarer) if {@code createBoss} itself couldn't resolve a
     *         {@code BossFixture} for the newly-grown border -- see its own warn log in that case.
     */
    public static DefeatOutcome forceDefeat(Level level, UUID bossId) {
        Optional<BossFixture> fixtureOpt = bosses(level);
        if (fixtureOpt.isEmpty()) {
            return new DefeatOutcome(
                    Result.notReady("BossFixture not available for level "
                            + level.dimension().location()),
                    Optional.empty());
        }
        BossFixture fixture = fixtureOpt.get();

        Optional<BossRecord> recordOpt = fixture.get(bossId);
        if (recordOpt.isEmpty()) {
            return new DefeatOutcome(
                    Result.notFound("No boss record for id " + bossId), Optional.empty());
        }
        BossRecord record = recordOpt.get();

        // Border Pregeneration: position is nullable until BOSS_JIG's own tick finalizes it (see
        // BossRecord's own doc) -- grow() needs a real center to place the next border around, so
        // a still-pending record can't be force-defeated yet. Checked before markDefeated() so a
        // rejected call leaves the record untouched rather than defeated with no border grown.
        if (!record.positioned()) {
            return new DefeatOutcome(
                    Result.validationRejected("Boss " + bossId
                            + " has no finalized position yet (home border still pregenerating)"
                            + " -- cannot force-defeat."),
                    Optional.empty());
        }

        // FRO_058: markDefeated() now guards against an already-defeated record itself, returning
        // false rather than mutating -- check it before running the grow/createBoss cascade.
        // Without this check, running /boss transform defeat a second time against the same
        // already-dead boss re-triggered the full grow-and-spawn cascade with no real defeat
        // behind it (shipped bug, playtest-verified in FRO_057, closed by boss.md's "Mutation
        // validation boundary" ruling).
        if (!fixture.markDefeated(bossId)) {
            return new DefeatOutcome(
                    Result.validationRejected("Boss " + bossId + " is already defeated."),
                    Optional.empty());
        }

        Result result = BorderAPI.grow(level, record.position());
        if (!result.isSuccess()) {
            OUT.warn("[Boss] forceDefeat(): BorderAPI.grow(level, " + record.position()
                    + ") failed for bossId=" + bossId + ": " + result.message()
                    + " -- not calling createBoss without a border.");
            return new DefeatOutcome(result, Optional.empty());
        }

        Optional<BossRecord> nextBoss = createBoss(level, result.border());
        BorderAPI.startPregeneration(level, result.border().id());
        return new DefeatOutcome(result, nextBoss);
    }
}
