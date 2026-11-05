package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.BossKind;
import com.arryn.frontiermode.boss.common.BossKinds;
import com.arryn.frontiermode.boss.common.DefeatOutcome;
import com.arryn.frontiermode.boss.common.fixture.BossCrudFacet;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossInfoFacet;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.boss.common.fixture.MaterializeOutcome;
import com.arryn.frontiermode.core.Dismissal;
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
    // FRO_081: facet resolvers, mirroring BorderAPI.PATH/CRUD/INFO's own shape -- Boss's
    // fixture stays reachable directly via bosses(Level) above (see BossFixture's own doc for
    // why, unlike Border's now-private resolveFixture()), but these give callers that only need
    // one specific facet the same resolve-and-map convenience BorderAPI's own callers get.
    // ---------------------------------------------------------------------

    public static Optional<BossCrudFacet> CRUD(Level level) {
        return bosses(level).map(f -> f.CRUD);
    }

    public static Optional<BossInfoFacet> INFO(Level level) {
        return bosses(level).map(f -> f.INFO);
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
     * The level's {@link BossRoster}, or empty when Satchel is not ready or the roster has not seen its first
     * parcel yet (the same "standby, don't crash" discipline as {@link #bosses(Level)}). {@code bosses}
     * answers the base records; the roster answers the {@link Boss}es built from the whole entries.
     */
    public static Optional<BossRoster> roster(Level level) {
        if (!Satchel.isReady()) {
            return Optional.empty();
        }
        return Satchel.trinket(FrontierKeys.BOSS_JIG, new LevelScope(level), BossRoster.class);
    }

    /**
     * Forces materialization of {@code bossId} regardless of chunk-loaded state -- used by
     * {@code /boss mob spawn}. Goes through the same {@link Boss} path as the tick, with the differences the
     * admin command has: it loads the chunk synchronously, works on a record that has no entity or a stale
     * one, and clears the respawn state. An entry that is not whole answers {@code NO_RECORD}.
     */
    public static MaterializeOutcome forceMaterialize(Level level, UUID bossId) {
        Optional<BossFixture> fixtureOpt = bosses(level);
        Optional<BossRoster> rosterOpt = roster(level);
        if (fixtureOpt.isEmpty() || rosterOpt.isEmpty()) {
            OUT.warn("[Boss] forceMaterialize(): BossFixture or roster not available for level "
                    + level.dimension().location() + " -- bossId=" + bossId);
            return MaterializeOutcome.NO_RECORD;
        }
        return rosterOpt.get().forceMaterialize(level, fixtureOpt.get(), bossId);
    }

    /**
     * Explicit admin removal (FRO_057's {@code /boss delete}), in the page's order: mark the {@link Boss}
     * retiring and despawn the live entity, then delete the record via {@link BossCrudFacet#remove(UUID)},
     * which announces the removal to the record's kind (the Keeper's marks the removed record's border
     * {@code pendingAttach} if it was still on-path). See
     * wiki/frontiermode/architecture/boss.md#aliveness-and-removal.
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

        Optional<BossRecord> recordOpt = fixture.CRUD.get(bossId);
        if (recordOpt.isEmpty()) {
            return false;
        }

        // The Boss is marked retiring before its body is discarded, so the generic rule never reads the
        // discard as a loss.
        roster(level).flatMap(roster -> roster.boss(bossId)).ifPresent(Boss::retire);

        // FRO_061: despawn the live entity, silently -- see this method's own doc for why
        // discard() (not kill()) is the deliberate choice.
        recordOpt
                .filter(BossRecord::materialized)
                .flatMap(record -> Satchel.require().egress().getEntity(level, record.bossEntityId()))
                .ifPresent(Entity::discard);

        return fixture.CRUD.remove(bossId);
    }

    /**
     * Force-defeats a boss record without combat (boss.md § Commands and the Info report; {@code /boss defeat}). The
     * base marks the record defeated, clears the respawn state, dismisses a loaded body, schedules the flush, then
     * calls the kind's {@code onDefeat} with the record's own position, no body and no credited player, and returns its
     * outcome; a boss with no {@code Persistent} then drops its record. It pays no reward -- an admin command
     * force-defeating bosses for loot is exactly the riskless exploit a reward should not have -- and the combat path
     * and this one share only the kind's hook. Nothing is mutated unless every check passes, so a rejected call leaves
     * the record untouched rather than defeated with nothing done.
     *
     * @return the kind's outcome, or one that did not go through if the level's boss data is not resolvable yet,
     *         there is no such record, its entry is not whole, it has no position yet, or it is already defeated
     */
    public static DefeatOutcome forceDefeat(Level level, UUID bossId) {
        Optional<BossFixture> fixtureOpt = bosses(level);
        if (fixtureOpt.isEmpty()) {
            return new DefeatOutcome(false, "boss data is not available for level "
                    + level.dimension().location() + " yet.");
        }
        BossFixture fixture = fixtureOpt.get();

        Optional<BossRecord> recordOpt = fixture.CRUD.get(bossId);
        if (recordOpt.isEmpty()) {
            return new DefeatOutcome(false, "there is no boss record for id " + Ids.shortId(bossId) + ".");
        }
        BossRecord record = recordOpt.get();

        // A Boss exists only for a whole entry (the base record and the kind's own). An entry that is not whole
        // waits, and nothing is defeated from it.
        Optional<Boss> bossOpt = roster(level).flatMap(roster -> roster.boss(bossId));
        if (bossOpt.isEmpty()) {
            return new DefeatOutcome(false, "its entry is not whole yet (the kind's own record is missing) --"
                    + " cannot force-defeat.");
        }
        Boss boss = bossOpt.get();
        BossKind kind = BossKinds.get(record.kind()).orElseThrow();

        // Border Pregeneration: position is nullable until BOSS_JIG's own tick finalizes it (see BossRecord's own
        // doc) -- a kind needs a real place to defeat the boss at, so a still-pending record can't be
        // force-defeated yet. Checked before markDefeated() so a rejected call leaves the record untouched.
        if (!record.positioned()) {
            return new DefeatOutcome(false, "it has no finalized position yet (home border still pregenerating)"
                    + " -- cannot force-defeat.");
        }

        // FRO_058: markDefeated() guards against an already-defeated record itself, returning false rather than
        // mutating -- without this check, forcing a defeat a second time against the same dead boss re-ran the
        // whole cascade with no real defeat behind it (boss.md's "Mutation validation boundary").
        if (!fixture.CRUD.markDefeated(bossId)) {
            return new DefeatOutcome(false, "it is already defeated.");
        }

        // A defeat clears the Boss's respawn state, and the dismissal below is the project's own removal of the
        // body, so the Boss is marked retiring first.
        boss.clearRespawnState();
        boss.retire();

        // A forced defeat has no kill, so the boss's body would otherwise stay: it leaves in a puff. discard(), not
        // kill(), so the real-death cascade doesn't re-enter (see removeBoss). One that is unloaded now is
        // dismissed by its Boss when it next attaches.
        if (record.materialized()) {
            Satchel.require().egress().getEntity(level, record.bossEntityId())
                    .ifPresent(entity -> {
                        if (entity instanceof net.minecraft.world.entity.Mob mob) {
                            Dismissal.dismiss(mob);
                        }
                    });
        }

        // FRO_108: the defeat mark itself is a real mutation worth protecting even when the kind's cascade then
        // withholds everything -- see boss.md's "Cascade durability".
        roster(level).ifPresent(BossRoster::scheduleFlush);

        DefeatOutcome outcome = kind.onDefeat(level, bossId, record.position(), Optional.empty(), Optional.empty());
        boss.dropIfTransient(fixture, "was force-defeated");
        return outcome;
    }
}
