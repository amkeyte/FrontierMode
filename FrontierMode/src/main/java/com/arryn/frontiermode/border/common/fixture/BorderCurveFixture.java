package com.arryn.frontiermode.border.common.fixture;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sibling fixture to {@link BordersFixture} (and {@link NavigatorFixture}) inside
 * {@link com.arryn.frontiermode.border.common.bundle.BordersBundle} -- zero-to-many named
 * intensity curves per border, per RM_FRO_027 ("Janet") /
 * wiki/frontiermode/architecture/border-curve.md. Registered alongside its siblings in
 * {@code BorderModule.init()}.
 *
 * <p>No tick wiring, per that page's own "Data model" section -- these are static descriptors,
 * set once and read on demand, unlike {@link NavigatorFixture}'s periodic recomputation (which
 * this fixture has none of) or {@link BorderPregenFixture}'s throttled tick-driven work.
 *
 * <p>Deletion cascade lives in {@code BorderAPI.removeBorder} (see that page's "Deletion:
 * explicit, not orphaned" section) -- {@link #removeForBorder(UUID)} is the method it calls,
 * chosen over an existing Satchel referential-integrity mechanism since none was found for this
 * shape of problem; a Lead Dev call flagged in this fixture's own doc rather than the wiki.
 */
public final class BorderCurveFixture extends SatchelFixture {

    private static final String KEY_CURVES = "curves";

    private final List<BorderCurve> curves = new ArrayList<>();

    public BorderCurveFixture() {
        registerCustom(KEY_CURVES, this::saveCurves, this::loadCurves);
    }

    private void saveCurves(CompoundTag root) {
        Satchel.requireServer();

        ListTag list = new ListTag();
        for (BorderCurve curve : curves) {
            list.add(BorderCurve.save(curve));
        }
        root.put(KEY_CURVES, list);
    }

    private void loadCurves(CompoundTag root) {
        if (!root.contains(KEY_CURVES, Tag.TAG_LIST)) {
            return;
        }
        curves.clear();

        ListTag list = root.getList(KEY_CURVES, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            // Same discipline BossFixture.loadBosses/BordersFixture.loadBorders follow: one
            // malformed record shouldn't abort hydration of every other curve on this level.
            try {
                curves.add(BorderCurve.load((CompoundTag) t));
            } catch (RuntimeException e) {
                OUT.warn("[BorderCurve] Skipping malformed curve record during load: " + e);
            }
        }
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public List<BorderCurve> forBorder(UUID borderId) {
        return curves.stream()
                .filter(c -> c.borderId().equals(borderId))
                .collect(Collectors.toUnmodifiableList());
    }

    public Optional<BorderCurve> forBorder(UUID borderId, String purpose) {
        return curves.stream()
                .filter(c -> c.borderId().equals(borderId) && c.purpose().equals(purpose))
                .findFirst();
    }

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------

    public BorderCurve create(UUID borderId, String purpose, Shape shape, double steepness) {
        Satchel.requireServer();
        Objects.requireNonNull(borderId, "borderId");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(shape, "shape");

        BorderCurve curve = new BorderCurve(UUID.randomUUID(), borderId, purpose, shape, steepness);
        curves.add(curve);
        markDirty();
        return curve;
    }

    /**
     * Upsert: replaces the named {@code (borderId, purpose)} curve with a fresh one carrying
     * {@code shape}/{@code steepness} if one already exists (a new record, new {@code id} --
     * nothing outside this fixture holds a curve by its own {@code id}, every real caller resolves
     * via {@link #forBorder(UUID, String)}), or creates one if absent -- same net effect as
     * {@link #create} on a border with no such curve yet.
     *
     * <p>Added for exactly the gap {@code create}-if-absent helpers like
     * {@code BossTellFixture.createTellCurveIfAbsent}/{@code BossGuardiansFixture
     * .ensureGuardianCurves} can't cover on their own: a curve's shape/steepness is "safe
     * baseline, replace later" tuning territory by design, and this whole cluster is still being
     * played and adjusted -- a live world that already seeded a curve under the old constants
     * would otherwise be stuck with them until the world resets, since {@code create}-if-absent
     * never touches an existing record. This lets a consumer's own "ensure current shape" helper
     * reconcile forward instead.
     */
    public BorderCurve replace(UUID borderId, String purpose, Shape shape, double steepness) {
        Satchel.requireServer();
        Objects.requireNonNull(borderId, "borderId");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(shape, "shape");

        curves.removeIf(c -> c.borderId().equals(borderId) && c.purpose().equals(purpose));
        BorderCurve curve = new BorderCurve(UUID.randomUUID(), borderId, purpose, shape, steepness);
        curves.add(curve);
        markDirty();
        return curve;
    }

    /**
     * Purges every curve referencing {@code borderId} -- the delete-cascade
     * {@code BorderAPI.removeBorder} calls, per this fixture's own class doc.
     *
     * @return the number of records removed (0 is a normal, expected outcome -- a border with no
     *         curves is the common case, not an error).
     */
    public int removeForBorder(UUID borderId) {
        Satchel.requireServer();
        Objects.requireNonNull(borderId, "borderId");

        int before = curves.size();
        curves.removeIf(c -> c.borderId().equals(borderId));
        int removed = before - curves.size();

        if (removed > 0) {
            markDirty();
        }
        return removed;
    }

}
