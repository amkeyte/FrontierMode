package com.arryn.frontiermode.border.common.fixture;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.LogicalSide;

import java.util.*;

/**
 * Facet-accessor surface onto Border's persisted state -- reach this fixture's data only through
 * {@code BorderAPI.PATH/CRUD/RULES/INFO(Level)}, never by holding a {@code BordersFixture}
 * reference directly (see the four facet fields' own doc comments below).
 *
 * <p><b>FRO_047:</b> the Java modifier stays {@code public} -- Satchel's
 * {@code FixtureKey<T extends SatchelFixture>} requires {@code T} to be accessible everywhere its
 * key is constructed and consumed, which reaches this class from three sibling packages that
 * aren't part of Border's own surface: {@code FrontierKeys} (top-level key registry),
 * {@code BordersBundle} (bundle wiring, {@code border.common.bundle}), and
 * {@code BorderModule.init()}'s own {@code FixtureDecl}/schema registration
 * ({@code border}). Making this literally package-private would require moving those out of
 * their own packages, which is a bigger restructuring than this ticket scopes. The encapsulation
 * this ticket actually cares about -- nothing outside Border reaching through the raw fixture --
 * is enforced instead by removing {@code BorderAPI.borders(Level)} and exposing only the four
 * facets below through {@code BorderAPI.PATH/CRUD/RULES/INFO(Level)}. Flagged per this ticket's
 * own item 5, not silently worked around.
 *
 * <p><b>FRO_050:</b> the above is the final word, not an open question --
 * {@code SatchelBundle.get(FixtureKey<T>)} stays public and unguarded (the generic mechanism
 * every module's own API class needs), so no compiler fix is possible; doc-comment discipline is
 * the accepted, permanent mitigation. See Border's wiki "Data model" section and Satchel's
 * Fixture "External Access Is Not Compiler-Enforced" section for the full ruling, not re-derived
 * here.
 */
public final class BordersFixture
        extends SatchelFixture {

    private static final String KEY_BORDERS = "borders";
    // Canonical progression order
    private static final String KEY_BORDER_PATH = "border_path";
    // RM_FRO_018: whether this level's path has ever had a border appended to it, ever -- set
    // once inside BordersPathFacet.grow()'s own append (every caller, not just the bootstrap
    // case), cleared by nothing. isEmpty() alone can't distinguish "brand-new world" from "an
    // admin removed every border," so this is what BorderModule's own ScopeEvent.Loaded bootstrap
    // hook checks instead -- see Border's "Known gaps" section.
    private static final String KEY_SEEDED = "seeded";
    /**
     * Not readiness-gated on its own -- reach only through {@code BorderAPI.PATH(Level)}, never
     * by holding a {@code BordersFixture} reference directly. Final ruling, FRO_050.
     */
    public final BordersPathFacet PATH = new BordersPathFacet(this);

    /**
     * Not readiness-gated on its own -- reach only through {@code BorderAPI.CRUD(Level)}, never
     * by holding a {@code BordersFixture} reference directly. Final ruling, FRO_050.
     */
    public final BordersCrudFacet CRUD = new BordersCrudFacet(this);

    /**
     * Not readiness-gated on its own -- reach only through {@code BorderAPI.RULES(Level)}, never
     * by holding a {@code BordersFixture} reference directly. Final ruling, FRO_050.
     */
    public final BordersRulesFacet RULES = new BordersRulesFacet(this);

    /**
     * Not readiness-gated on its own -- reach only through {@code BorderAPI.INFO(Level)}, never
     * by holding a {@code BordersFixture} reference directly. Final ruling, FRO_050.
     */
    public final BordersInfoFacet INFO = new BordersInfoFacet(this);

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------
    final List<UUID> borderPath = new ArrayList<>();
    private final List<Border> borders;
    private boolean seeded = false;

    // ---------------------------------------------------------------------
    // Construction + persistence registration
    // ---------------------------------------------------------------------
    public BordersFixture() {
        this.borders = new ArrayList<>();

        // Explicit persistence registration
        registerCustom(
                KEY_BORDERS,
                this::saveBorders,
                this::loadBorders
        );

        registerCustom(
                KEY_SEEDED,
                tag -> tag.putBoolean(KEY_SEEDED, seeded),
                tag -> {
                    if (tag.contains(KEY_SEEDED)) {
                        seeded = tag.getBoolean(KEY_SEEDED);
                    }
                }
        );
    }

    private void saveBorders(CompoundTag root) {
        requireServerSide();

        ListTag list = new ListTag();
        for (Border border : borders) {
            list.add(Border.save(border));
        }
        root.put(KEY_BORDERS, list);

        // NEW: border path
        ListTag pathTag = new ListTag();
        for (UUID id : borderPath) {
            pathTag.add(NbtUtils.createUUID(id));
        }
        root.put(KEY_BORDER_PATH, pathTag);
    }

    private void loadBorders(CompoundTag root) {

        if (!root.contains(KEY_BORDERS, Tag.TAG_LIST)) {
            return;
        }
        borders.clear();

        ListTag list = root.getList(KEY_BORDERS, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            // RM_FRO_013: Persistence's own documented contract is "Invalid fixture NBT -> Log +
            // skip" -- this loop didn't follow it. A single malformed or hand-edited border entry
            // (missing id, wrong tag type, a future format change) threw during Border.load's
            // tag.getUUID("id")/etc. and aborted hydration of the *entire* list for this world,
            // not just the one bad entry.
            try {
                borders.add(Border.load((CompoundTag) t));
            } catch (RuntimeException e) {
                OUT.warn("[Border] Skipping malformed border entry during load: " + e);
            }
        }


        // NEW: border path
        if (root.contains(KEY_BORDER_PATH, Tag.TAG_LIST)) {
            ListTag pathTag = root.getList(KEY_BORDER_PATH, Tag.TAG_INT_ARRAY);
            for (Tag tag : pathTag) {
                borderPath.add(NbtUtils.loadUUID(tag));
            }
        }
    }

    // ---------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------

    boolean seeded() {
        return seeded;
    }

    /**
     * Marks this level's path as seeded -- called once by {@link BordersPathFacet#grow()} inside
     * its own path append, covering every caller uniformly (organic growth, an admin command, a
     * future debug trigger). Never cleared once set, including by border removal -- see this
     * class's {@code KEY_SEEDED} field doc and Border's "Known gaps" wiki section for why
     * {@code isEmpty()} alone can't stand in for this.
     */
    void markSeeded() {
        if (!seeded) {
            seeded = true;
        }
    }

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------
    @Override
    public void onLoaded() {
        super.onLoaded();

        OUT.info("            Borders loaded: " + borders.size());
    }

    @Override
    public void onCreated() {
        super.onCreated();
        borderPath.clear();//cleanup
    }

    List<Border> all() {
        return Collections.unmodifiableList(borders);
    }

    //PROPOSAL
    // ------------------------------------------------------------------
    // New proposal entry
    // ------------------------------------------------------------------

    Optional<Border> get(UUID id) {
        return borders.stream()
                .filter(b -> b.id().equals(id))
                .findFirst();
    }

    boolean remove(UUID id) {
        requireServerSide();

        boolean removed = borders.removeIf(b -> b.id().equals(id));
        if (!removed) {
            return false;
        }

        // Referential integrity only
        borderPath.removeIf(pathId -> pathId.equals(id));

        markDirty();
        return true;
    }

    //TODO move this into super.
    void requireServerSide() {
        if(Satchel.require().side() == LogicalSide.CLIENT)
            throw new IllegalStateException();
    }

    void markPathDirty() {
        requireServerSide();
        markDirty();
    }

    /**
     * FRO_047: the level a proposal's default geometry and organic-growth math are computed
     * against -- moved here from the deleted {@code BorderLogic.resolveLevel()}, same body.
     * Shared by {@link BordersPathFacet#grow()} and {@link BorderProposal}'s own defaulting.
     */
    ServerLevel resolveLevel() {
        if (!(scope() instanceof LevelScope levelScope)) {
            throw new IllegalStateException("Non level scope.");
        }

        if (!(levelScope.level() instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("Non server level");
        }

        return serverLevel;
    }

    // ------------------------------------------------------------------
    // New accept path (temporary)
    // ------------------------------------------------------------------
    Border accept(BorderProposal proposal) {
        requireServerSide();

        Border border = proposal.create();

        borders.removeIf(b -> b.id().equals(border.id()));
        borders.add(border);

        markDirty();
        return border;    // if applicable
    }

    // ------------------------------------------------------------------
    // RM_FRO_015: fixLayers() bulk reassignment
    // ------------------------------------------------------------------

    /**
     * RM_FRO_015: the real fixLayers() reorder logic -- see Border Path & Layer Reconciliation
     * (architecture wiki) for the full design this implements. Bulk-reassigns
     * {@link Border#layer()} values to match {@code pathTargets} (built by
     * {@link BordersPathFacet#fixLayers()} from the current path order:
     * {@code target[borderPath.get(i)] = i}).
     *
     * <p><b>Simplified, 2026-08-20: no longer bumps off-path borders out of the way.</b> Earlier
     * revision moved any off-path border whose layer collided with the reserved
     * {@code [0, pathTargets.size())} range clear of it, because {@code BordersCrudFacet} used to
     * reject layer collisions. That guard is gone (project owner's design call -- Layer and Path
     * are definitionally unrelated, and {@code DefaultBorderRules.getRelevant()} already resolves a
     * same-layer overlap by nearest center; see {@code BordersCrudFacet}'s own doc
     * for the full reasoning), so a path member's layer can simply be set to its path index without
     * checking what any off-path border currently holds -- duplicate layers are legitimate, not a
     * collision to avoid.
     *
     * <p>Applied as one atomic batch replace against the internal {@code borders} list, one
     * {@link #markDirty()} for the whole batch -- not a loop of
     * {@link BordersCrudFacet#applyProposal} calls, simply to keep this as one clean revision bump
     * rather than {@code N}.
     *
     * <p>Returns the count of borders whose layer actually changed, plus the count of stale path
     * entries self-healed (see below) -- 0 means an honest no-op, path and layer order were
     * already consistent and clean.
     *
     * <p><b>Self-heals stale path entries, found by real playtest (RM_FRO_015, 2026-08-20):</b> a
     * {@code borderPath} entry whose UUID has no matching {@link Border} at all is real data
     * corruption -- old {@code fixLayers()} never walked the path against real borders, so nothing
     * before this method could ever have caught or cleaned one up, and it would otherwise
     * re-trigger the same warning below forever. Rather than just detect-and-skip it, this method
     * also removes it from {@code borderPath} -- there's nothing else a stale reference can
     * meaningfully do once found, and leaving it in place only guarantees the same warning fires on
     * every future call. Still logged loudly either way, so the fact that it happened isn't lost.
     */
    int reassignLayers(Map<UUID, Integer> pathTargets) {
        requireServerSide();

        List<Border> replacements = new ArrayList<>();
        List<UUID> phantomPathEntries = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : pathTargets.entrySet()) {
            Optional<Border> existing = get(entry.getKey());
            if (existing.isEmpty()) {
                // Real data corruption, not a normal transient state -- self-heal by dropping the
                // phantom reference from the path (see this method's own doc for why), and log
                // loudly so it isn't silently lost history, same "detect and log, don't paper over"
                // stance Boss's own reconciliation check takes for the equivalent mismatch.
                if (borderPath.contains(entry.getKey())) {
                    phantomPathEntries.add(entry.getKey());
                }
                OUT.warn("[Border] fixLayers(): " + entry.getKey() + " is in the reassignment set "
                        + "but has no matching border -- removing it from the path.");
                continue;
            }

            Border border = existing.get();
            int targetLayer = entry.getValue();
            if (border.layer() == targetLayer) {
                continue; // already correct -- not a change
            }

            replacements.add(new Border(
                    border.id(),
                    border.displayName(),
                    border.center(),
                    border.radius(),
                    targetLayer
            ));
        }

        boolean pathCleaned = !phantomPathEntries.isEmpty()
                && borderPath.removeAll(phantomPathEntries);

        if (replacements.isEmpty() && !pathCleaned) {
            return 0;
        }

        for (Border replacement : replacements) {
            borders.removeIf(b -> b.id().equals(replacement.id()));
            borders.add(replacement);
        }
        markDirty();

        return replacements.size() + phantomPathEntries.size();
    }
}
