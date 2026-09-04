package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * FRO_081: Boss's queries/diagnostics facet, mirroring {@code BordersInfoFacet}'s shape -- the
 * read-only tick-cadence queries {@code BOSS_JIG}'s own tick walks every cycle
 * ({@link #unmaterialized()}/{@link #unpositioned()}, see {@code boss.md}'s "Three questions,
 * three different mechanisms"), plus the defensive path/{@code BossFixture} reconciliation
 * diagnostic ({@link #reportMismatch}), moved here from {@code BossModule}'s own
 * {@code reconcilePathAgainstBossRecords()} body.
 *
 * <p><b>Edge-triggered mismatch logging, relocated:</b> {@code BossModule} used to track this
 * (FRO_062) in a {@code Map<Level, Set<Integer>>} keyed by level, purely because the check itself
 * lived on the static, level-agnostic {@code BossModule} class. This facet is already
 * one-per-{@link BossFixture} -- and {@code BossFixture} is already one-per-level -- so a plain
 * instance field carries the identical "last reported mismatch for this level" state without the
 * map, the same simplification every other per-level facet field on this fixture already gets for
 * free.
 */
public final class BossInfoFacet {
    private final BossFixture setting;

    // FRO_062 origin, relocated FRO_081 -- see this class's own doc.
    private Set<Integer> lastReportedMismatch = Set.of();

    BossInfoFacet(BossFixture setting) {
        this.setting = setting;
    }

    /**
     * See {@link BossFixture#unmaterialized()}.
     */
    public List<BossRecord> unmaterialized() {
        return setting.unmaterialized();
    }

    /**
     * See {@link BossFixture#unpositioned()}.
     */
    public List<BossRecord> unpositioned() {
        return setting.unpositioned();
    }

    /**
     * See {@link BossFixture#layers()}.
     */
    public Set<Integer> layers() {
        return setting.layers();
    }

    /**
     * FRO_082: see {@link BossFixture#pendingAttach()}.
     */
    public Set<UUID> pendingAttach() {
        return setting.pendingAttach();
    }

    /**
     * FRO_083: see {@link BossFixture#anyAliveWithBorderId(UUID)}.
     */
    public boolean anyAliveWithBorderId(UUID borderId) {
        return setting.anyAliveWithBorderId(borderId);
    }

    /**
     * FRO_081: the diagnostic half of {@code BossModule}'s old
     * {@code reconcilePathAgainstBossRecords()} -- resolving {@code BorderAPI.PATH}/{@code CRUD}
     * and walking the path to build {@code pathLayers} stays in {@code BossModule}
     * (cross-module composition, the same orchestration-layer precedent
     * {@code BossModule.resolveHomeBorder()} already sets); this method takes the resulting map
     * and does the edge-triggered compare-and-log against this fixture's own
     * {@link #layers()} -- unchanged FRO_062 behavior (log only on a real transition, not every
     * tick the same mismatch persists), just relocated onto this already-level-scoped facet.
     *
     * <p><b>FRO_082:</b> {@code pathLayers} widened from {@code Set<Integer>} to
     * {@code Map<Integer, UUID>} (layer value -> that layer's border id) so a gap can be checked
     * against {@link #pendingAttach()} before logging -- see boss.md's "Boss-less path layers and
     * attach" section. A gap whose border id is in {@code pendingAttach} logs at {@code OUT.info}
     * ("awaiting attach", the sanctioned wait-state a boss-less {@code pathGrow()} or an on-path
     * {@code /boss delete} produces) instead of the loud {@code OUT.warn} "real data bug" -- an
     * uncovered gap still logs exactly as before. Both halves feed the same
     * {@code lastReportedMismatch} edge-trigger (keyed on the full {@code missing} set, not just
     * the uncovered half) so a transition in either direction -- a gap closing, or a gap moving
     * from uncovered to covered by a fresh {@code pendingAttach} entry -- still re-logs once,
     * same as today.
     */
    public void reportMismatch(Level level, Map<Integer, UUID> pathLayers) {
        Set<Integer> missing = new HashSet<>(pathLayers.keySet());
        missing.removeAll(setting.layers());

        if (missing.equals(lastReportedMismatch)) {
            return;
        }

        if (missing.isEmpty()) {
            OUT.info("[Boss] Reconciliation: previously-mismatched layer(s) " + lastReportedMismatch
                    + " no longer mismatched in level " + level.dimension().location() + ".");
            lastReportedMismatch = Set.of();
            return;
        }

        Set<UUID> pending = setting.pendingAttach();
        Set<Integer> awaitingAttach = new HashSet<>();
        Set<Integer> uncovered = new HashSet<>();
        for (Integer layer : missing) {
            UUID borderId = pathLayers.get(layer);
            if (borderId != null && pending.contains(borderId)) {
                awaitingAttach.add(layer);
            } else {
                uncovered.add(layer);
            }
        }

        if (!awaitingAttach.isEmpty()) {
            OUT.info("[Boss] Reconciliation: path layer(s) " + awaitingAttach + " have no boss"
                    + " record in level " + level.dimension().location() + " -- awaiting"
                    + " /boss attach (pendingAttach), not a data bug.");
        }
        if (!uncovered.isEmpty()) {
            OUT.warn("[Boss] Reconciliation: path has border(s) at layer(s) " + uncovered
                    + " with no matching BossFixture record in level " + level.dimension().location()
                    + " -- real data bug (missed call site, crash between paired calls, or manual"
                    + " world editing), not a normal transient state.");
        }
        lastReportedMismatch = Set.copyOf(missing);
    }
}
