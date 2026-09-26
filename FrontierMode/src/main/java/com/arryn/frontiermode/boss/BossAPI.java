package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BordersPathFacet;
import com.arryn.frontiermode.border.common.fixture.Result;
import com.arryn.frontiermode.boss.common.fixture.BossCrudFacet;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossGuardiansFixture;
import com.arryn.frontiermode.boss.common.fixture.BossInfoFacet;
import com.arryn.frontiermode.boss.common.fixture.BossPublicFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.boss.common.fixture.BossRulesFacet;
import com.arryn.frontiermode.boss.common.fixture.BossTellFixture;
import com.arryn.frontiermode.boss.common.fixture.GuardianPublicFixture;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.jig.guts.SatchelException;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
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

        // FRO_150: Satchel.tryReadyScopeInfo() collapses the tryScopeInfo()/info.isReady() two
        // steps this used to hand-write into one call.
        var infoOpt = Satchel.tryReadyScopeInfo(FrontierKeys.BOSS_JIG, scope);
        if (infoOpt.isEmpty()) {
            OUT.debug("[BossAPI] bosses(): scope not ready -> Optional.empty level="
                    + level.dimension().location());
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

    // ---------------------------------------------------------------------
    // FRO_081: facet resolvers, mirroring BorderAPI.PATH/CRUD/RULES/INFO's own shape -- Boss's
    // fixture stays reachable directly via bosses(Level) above (see BossFixture's own doc for
    // why, unlike Border's now-private resolveFixture()), but these give callers that only need
    // one specific facet the same resolve-and-map convenience BorderAPI's own callers get.
    // ---------------------------------------------------------------------

    public static Optional<BossCrudFacet> CRUD(Level level) {
        return bosses(level).map(f -> f.CRUD);
    }

    public static Optional<BossRulesFacet> RULES(Level level) {
        return bosses(level).map(f -> f.RULES);
    }

    public static Optional<BossInfoFacet> INFO(Level level) {
        return bosses(level).map(f -> f.INFO);
    }

    /**
     * FRO_087 (Janice): resolves {@link BossTellFixture} for {@code level} -- same
     * "standby, don't crash" discipline as {@link #bosses(Level)}. Used at boss-creation call
     * sites (via {@code BossModule}) and by any future caller that needs to read or mutate tell
     * records directly.
     */
    public static Optional<BossTellFixture> bossTells(Level level) {
        if (!Satchel.isReady()) {
            OUT.debug("[BossAPI] bossTells(): Satchel not ready yet -> Optional.empty level="
                    + level.dimension().location());
            return Optional.empty();
        }

        LevelScope scope = new LevelScope(level);

        // FRO_150: Satchel.tryReadyScopeInfo() collapses the tryScopeInfo()/info.isReady() two
        // steps this used to hand-write into one call.
        var infoOpt = Satchel.tryReadyScopeInfo(FrontierKeys.BOSS_JIG, scope);
        if (infoOpt.isEmpty()) {
            OUT.debug("[BossAPI] bossTells(): scope not ready -> Optional.empty level="
                    + level.dimension().location());
            return Optional.empty();
        }

        try {
            return levelJig()
                    .getOrCreate(scope, FrontierKeys.BOSS_BUNDLE)
                    .get(FrontierKeys.BOSS_TELL);
        } catch (RuntimeException e) {
            throw new SatchelException.AccessFailed(
                    "Failed to resolve BossTellFixture for level " + level.dimension().location(), e);
        }
    }

    /**
     * FRO_121 (Intuition/Resonance): resolves the boss identity at the level's current
     * {@code BordersPathFacet} tip -- "Intuition/Resonance resolve 'the current Path Tip'
     * exactly once, at lock time, to get a boss identity" per special-compass.md's "One shared
     * core" section. A border can carry more than one boss (themed multi-mob encounters share
     * one {@code borderId}, per boss.md's cascade-gating notes); this returns any one alive
     * match rather than picking among several, since the design doesn't distinguish them here.
     * {@code Optional.empty()} if the level has no path yet, no tip, or the tip border has no
     * living boss record.
     */
    public static Optional<UUID> pathTipBossId(Level level) {
        Optional<BossFixture> fixtureOpt = bosses(level);
        if (fixtureOpt.isEmpty()) {
            return Optional.empty();
        }
        Optional<UUID> tipBorderIdOpt = BorderAPI.PATH(level).flatMap(BordersPathFacet::tip).map(Border::id);
        if (tipBorderIdOpt.isEmpty()) {
            return Optional.empty();
        }
        UUID tipBorderId = tipBorderIdOpt.get();

        return fixtureOpt.get().all().stream()
                .filter(BossRecord::alive)
                .filter(r -> r.borderId().equals(Optional.of(tipBorderId)))
                .map(BossRecord::bossId)
                .findFirst();
    }

    /**
     * FRO_120 (Cairn): public query surface boss.md's own "Aliveness checking and despawn"
     * section describes but never actually built until now -- gates Cairn's own pulse (alive:
     * point at {@code lastKnownBossPos}; dead: a fresh random sphere point, per cairn.md's "The
     * pulse" section). {@code false} for a record that doesn't exist or isn't resolvable yet, the
     * same "standby, don't crash" default every other {@code BossAPI} boolean/{@code Optional}
     * query in this class uses.
     */
    public static boolean isBossAlive(Level level, UUID bossId) {
        return bosses(level)
                .flatMap(fixture -> fixture.CRUD.get(bossId))
                .map(BossRecord::alive)
                .orElse(false);
    }

    /**
     * FRO_118: the live-position resolver shared by the Admin Compass path and (later) Resonance,
     * and reused as-is by Cairn's own {@code lastKnownBossPos} pulse (FRO_120) -- one resolver, not
     * two, per special-compass.md's "Live position resolution" section. Reads {@code bossId}'s
     * actual materialized mob position via {@code ForgeEgress.getEntity} if the entity is
     * currently loaded, falling back to the record's own (possibly unfinalized) {@link
     * BossRecord#position()} otherwise -- the same "standby, don't crash" degrade every other
     * cross-module Boss query in this codebase uses. {@code Optional.empty()} only when the
     * record itself doesn't exist or the level's Boss data isn't resolvable yet; an unmaterialized
     * or unpositioned record still returns whatever {@code position()} holds (nullable, so this
     * still filters it through {@code Optional.ofNullable}).
     */
    public static Optional<BlockPos> resolveLivePosition(Level level, UUID bossId) {
        Optional<BossFixture> fixtureOpt = bosses(level);
        if (fixtureOpt.isEmpty()) {
            return Optional.empty();
        }
        Optional<BossRecord> recordOpt = fixtureOpt.get().CRUD.get(bossId);
        if (recordOpt.isEmpty()) {
            return Optional.empty();
        }
        BossRecord record = recordOpt.get();

        if (record.materialized()) {
            Optional<net.minecraft.world.entity.Entity> entityOpt =
                    Satchel.require().egress().getEntity(level, record.bossEntityId());
            if (entityOpt.isPresent()) {
                return Optional.of(entityOpt.get().blockPosition());
            }
        }

        return Optional.ofNullable(record.position());
    }

    /**
     * FRO_145: the client-syncable boss marker fixture, on either side. Unlike the resolvers above
     * this never logs and never throws -- the client eye layer calls it from the render path, where
     * "not ready yet" is the normal state for the first moments after joining a world and a
     * per-frame debug line or exception would be noise. See boss.md's "Client-side visual marker".
     */
    public static Optional<BossPublicFixture> publicMarkers(Level level) {
        if (!Satchel.isReady()) {
            return Optional.empty();
        }
        LevelScope scope = new LevelScope(level);
        if (Satchel.tryReadyScopeInfo(FrontierKeys.BOSS_PUBLIC_JIG, scope).isEmpty()) {
            return Optional.empty();
        }
        try {
            var jig = (LevelJig) foundation().requireJigInfo(FrontierKeys.BOSS_PUBLIC_JIG).jig;
            return jig.getOrCreate(scope, FrontierKeys.BOSS_PUBLIC_BUNDLE).get(FrontierKeys.BOSS_PUBLIC);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Resolves {@link BossGuardiansFixture} for {@code level} -- same "standby, don't crash"
     * discipline as {@link #bosses(Level)}, mirroring {@link #bossTells} exactly. Used by
     * {@code BossModule.onMobSpawnFinalize}.
     */
    public static Optional<BossGuardiansFixture> guardians(Level level) {
        if (!Satchel.isReady()) {
            OUT.debug("[BossAPI] guardians(): Satchel not ready yet -> Optional.empty level="
                    + level.dimension().location());
            return Optional.empty();
        }

        LevelScope scope = new LevelScope(level);

        // FRO_150: Satchel.tryReadyScopeInfo() collapses the tryScopeInfo()/info.isReady() two
        // steps this used to hand-write into one call.
        var infoOpt = Satchel.tryReadyScopeInfo(FrontierKeys.BOSS_JIG, scope);
        if (infoOpt.isEmpty()) {
            OUT.debug("[BossAPI] guardians(): scope not ready -> Optional.empty level="
                    + level.dimension().location());
            return Optional.empty();
        }

        try {
            return levelJig()
                    .getOrCreate(scope, FrontierKeys.BOSS_BUNDLE)
                    .get(FrontierKeys.BOSS_GUARDIANS);
        } catch (RuntimeException e) {
            throw new SatchelException.AccessFailed(
                    "Failed to resolve BossGuardiansFixture for level " + level.dimension().location(), e);
        }
    }

    /**
     * {@link #guardians(Level)} for hot paths -- the hitbox {@code Size} handler runs for every
     * entity's dimension refresh, including from the {@code Entity} constructor. Never logs, never
     * throws; empty for anything not resolvable yet.
     */
    public static Optional<BossGuardiansFixture> guardiansQuiet(Level level) {
        if (!Satchel.isReady()) {
            return Optional.empty();
        }
        LevelScope scope = new LevelScope(level);
        if (Satchel.tryReadyScopeInfo(FrontierKeys.BOSS_JIG, scope).isEmpty()) {
            return Optional.empty();
        }
        try {
            return levelJig().getOrCreate(scope, FrontierKeys.BOSS_BUNDLE).get(FrontierKeys.BOSS_GUARDIANS);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * The client-visible guardian record on either side. Like {@link #publicMarkers(Level)}: never
     * logs, never throws -- "not ready yet" is the normal state for the first moments after joining
     * a world, and this is called from render and tick paths.
     */
    public static Optional<GuardianPublicFixture> guardianMarkers(Level level) {
        if (!Satchel.isReady()) {
            return Optional.empty();
        }
        LevelScope scope = new LevelScope(level);
        if (Satchel.tryReadyScopeInfo(FrontierKeys.GUARDIAN_PUBLIC_JIG, scope).isEmpty()) {
            return Optional.empty();
        }
        try {
            var jig = (LevelJig) foundation().requireJigInfo(FrontierKeys.GUARDIAN_PUBLIC_JIG).jig;
            return jig.getOrCreate(scope, FrontierKeys.GUARDIAN_PUBLIC_BUNDLE).get(FrontierKeys.GUARDIAN_PUBLIC);
        } catch (RuntimeException e) {
            return Optional.empty();
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
                    + level.dimension().location() + " -- border " + Ids.shortId(border.id()) + " gets no boss record.");
            return Optional.empty();
        }

        // FRO_058: BossFixture.create() now returns Optional<BossRecord> itself (empty on a
        // rejected negative layer) -- no wrapping needed here anymore.
        // FRO_081: routed through the new BossCrudFacet rather than calling fixture.create()
        // directly -- a pure pass-through (BossCrudFacet.create() forwards to the exact same
        // BossFixture.create() call this always made), done here so this call site (and every
        // caller of createBoss(), including BossModule.onBordersScopeLoaded/onLivingDeath/this
        // class's own forceDefeat() below) benefits from the new facet structure through
        // BossAPI's own sanctioned facade, without every caller needing to change to reach the
        // facet directly.
        // FRO_082: create(layer, borderId) instead of the bare create(layer) -- every caller of
        // createBoss() is, by construction, paired with real border-growth (that's this method's
        // whole contract: "for border"), so the new record's borderId is always set to
        // border.id() here, never left empty. The off-path (/boss add) entry point stays on
        // BossFixture.create(int)/BossCrudFacet.create(int) directly, unaffected.
        return fixtureOpt.get().CRUD.create(border.layer(), border.id());
    }

    /**
     * Explicit admin removal (FRO_057's {@code /boss delete}) -- despawns/deletes via
     * {@link BossCrudFacet#remove(UUID)}, then (FRO_082, FRO_063's ruling) marks the removed
     * record's border {@code pendingAttach} if it was still on-path. See
     * wiki/frontiermode/architecture/boss.md#despawn-and-record-removal.
     *
     * <p><b>FRO_061 (project owner ruling):</b> if the removed record was materialized, its live
     * entity is despawned via {@code Satchel.require().egress().getEntity(...)} -- the same
     * sanctioned live-entity surface {@link #resolveLivePosition} already uses, not a bare
     * fixture-record edit. {@code discard()}, deliberately not {@code kill()}: deletion is an
     * administrative removal ("get rid of this record"), not a "boss died" event, and
     * {@code kill()} would run real death handling and could re-enter {@code BossJigHandlers
     * .onMobDied}'s own defeat-detection listener -- growing a border and queuing a next boss for
     * an admin cleanup action, which is exactly the cascade this ruling says must not happen. An
     * unmaterialized record has no entity to despawn and behaves exactly as {@code remove()} did
     * before this change.
     *
     * @return true if a record with this id existed and was removed; false otherwise (same
     *         contract as {@link BossFixture#remove(UUID)}/{@link BossCrudFacet#remove(UUID)}).
     */
    public static boolean removeBoss(Level level, UUID bossId) {
        Optional<BossFixture> fixtureOpt = bosses(level);
        if (fixtureOpt.isEmpty()) {
            OUT.warn("[Boss] removeBoss(): BossFixture not available for level "
                    + level.dimension().location() + " -- bossId=" + bossId);
            return false;
        }
        BossFixture fixture = fixtureOpt.get();

        // Captured before remove() -- the record (and its borderId) is gone from the fixture
        // once remove() returns true.
        Optional<BossRecord> recordOpt = fixture.CRUD.get(bossId);

        boolean removed = fixture.CRUD.remove(bossId);
        if (!removed) {
            return false;
        }

        // FRO_061: despawn the live entity, silently -- see this method's own doc for why
        // discard() (not kill()) is the deliberate choice.
        recordOpt
                .filter(BossRecord::materialized)
                .flatMap(record -> Satchel.require().egress().getEntity(level, record.bossEntityId()))
                .ifPresent(Entity::discard);

        // FRO_082: mark the removed record's border pendingAttach, same sanctioned wait-state a
        // boss-less pathGrow() produces -- but only if it actually had a borderId (a hand-placed
        // off-path /boss add-ed record never does) and that border is still genuinely on the
        // level's path (a border removed from the path some other way shouldn't get queued for
        // an attach action that no longer makes sense).
        recordOpt.flatMap(BossRecord::borderId).ifPresent(borderId -> {
            if (isOnPath(level, borderId)) {
                fixture.CRUD.addPendingAttach(borderId);
            }
        });

        return true;
    }

    private static boolean isOnPath(Level level, UUID borderId) {
        return BorderAPI.PATH(level).map(path -> path.all().contains(borderId)).orElse(false);
    }

    /**
     * Bundles {@link #forceDefeat}'s outcome -- {@code borderResult} is exactly what
     * {@code BorderAPI.grow} returned (its own {@code border()} is the newly grown
     * <em>border</em>, not a boss), and {@code nextBoss} is the actual {@link BossRecord}
     * {@link #createBoss} produced for it, if any. FRO_057 playtest turned up that reporting
     * {@code borderResult.border().id()} as "the next boss" was actively wrong -- a border id
     * and a boss id are different UUIDs, and the chat feedback was showing the former labeled as
     * the latter.
     *
     * <p><b>FRO_083:</b> {@code growthGated} is {@code true} only for the two new cascade-gating
     * outcomes ({@code borderId} empty -- an off-path {@code /boss add}-ed record was never
     * meant to grow anything; or another record on the same {@code borderId} is still alive --
     * "last one standing" not yet satisfied). Both leave {@code borderResult} as a
     * {@code validationRejected} {@link Result} (there's no {@code BorderAPI.grow} outcome to
     * report -- {@code grow} was never called), but the defeat mutation itself already
     * succeeded by this point ({@code markDefeated} already ran) -- {@code growthGated} is what
     * lets a caller like {@code BossCommandHandler.transformDefeat} tell "this genuinely failed"
     * from "this succeeded, growth correctly withheld" instead of reporting the latter as a
     * command failure.
     */
    public record DefeatOutcome(Result borderResult, Optional<BossRecord> nextBoss, boolean growthGated) {
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
                    Optional.empty(), false);
        }
        BossFixture fixture = fixtureOpt.get();

        Optional<BossRecord> recordOpt = fixture.CRUD.get(bossId);
        if (recordOpt.isEmpty()) {
            return new DefeatOutcome(
                    Result.notFound("No boss record for id " + bossId), Optional.empty(), false);
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
                    Optional.empty(), false);
        }

        // FRO_058: markDefeated() now guards against an already-defeated record itself, returning
        // false rather than mutating -- check it before running the grow/createBoss cascade.
        // Without this check, running /boss transform defeat a second time against the same
        // already-dead boss re-triggered the full grow-and-spawn cascade with no real defeat
        // behind it (shipped bug, playtest-verified in FRO_057, closed by boss.md's "Mutation
        // validation boundary" ruling).
        // FRO_081: routed through BossCrudFacet, a pure pass-through onto the exact same
        // BossFixture.markDefeated() call this always made.
        if (!fixture.CRUD.markDefeated(bossId)) {
            return new DefeatOutcome(
                    Result.validationRejected("Boss " + bossId + " is already defeated."),
                    Optional.empty(), false);
        }

        // A forced defeat kills the boss's guardians uncredited: drops, no XP.
        GuardianJigHandlers.onBossDefeated(level, bossId, null);

        // FRO_108: the defeat mark itself is a real mutation worth protecting even when cascade
        // gating below stops growth right here -- see boss.md's "Cascade durability".
        fixture.scheduleForceFlush();

        // FRO_083 (boss.md "Cascade gating: on-path, and last one standing"), mirrors the
        // identical check BossModule.onLivingDeath() runs right after its own markDefeated() --
        // see that method's own comment for the full reasoning. The defeat mutation above already
        // committed either way; only the grow/createBoss cascade is gated here.
        if (record.borderId().isEmpty()) {
            return new DefeatOutcome(
                    Result.validationRejected("Boss " + bossId + " was never on-path (borderId"
                            + " empty, e.g. a hand-placed /boss add) -- force-defeated, but no"
                            + " border growth to trigger."),
                    Optional.empty(), true);
        }
        UUID borderId = record.borderId().get();
        if (fixture.INFO.anyAliveWithBorderId(borderId)) {
            return new DefeatOutcome(
                    Result.validationRejected("Boss " + bossId + " force-defeated, but another"
                            + " boss on border " + borderId + " is still alive -- border growth"
                            + " withheld until every boss on this border falls."),
                    Optional.empty(), true);
        }

        Result result = BorderAPI.grow(level, record.position());
        if (!result.isSuccess()) {
            OUT.warn("[Boss] forceDefeat(): BorderAPI.grow(level, " + record.position()
                    + ") failed for bossId=" + bossId + ": " + result.message()
                    + " -- not calling createBoss without a border.");
            return new DefeatOutcome(result, Optional.empty(), false);
        }

        Optional<BossRecord> nextBoss = createBoss(level, result.border());
        BorderAPI.startPregeneration(level, result.border().id());
        BossTellFixture.createTellCurveIfAbsent(level, result.border().id());
        // RM_FRO_029 (Gloria): same paired-creation discipline -- this is one of the three call
        // sites ensureGuardianCurves runs from (the other two are
        // BossJigHandlers.onBordersScopeLoaded/onMobDied).
        BossGuardiansFixture.ensureGuardianCurves(level, result.border().id());
        return new DefeatOutcome(result, nextBoss, false);
    }
}
