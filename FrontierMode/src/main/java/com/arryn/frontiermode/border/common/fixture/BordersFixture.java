package com.arryn.frontiermode.border.common.fixture;

import com.arryn.frontiermode.border.server.rules.BorderLogic;
import com.arryn.frontiermode.border.server.rules.DefaultBorderRules;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraftforge.fml.LogicalSide;

import java.util.*;

/**
 * BorderSetting class to become package private. Use facet accessors instead.
 */
public final class BordersFixture
        extends SatchelFixture
        implements BorderAuthority {

    private static final String KEY_BORDERS = "borders";
    // Canonical progression order
    private static final String KEY_BORDER_PATH = "border_path";
    public final BordersPathFacet PATH = new BordersPathFacet(this);
    ;
    public final BordersCrudFacet CRUD = new BordersCrudFacet(this);
    //RULES
    public final BordersRulesFacet RULES = new BordersRulesFacet(this);
    public final BordersInfoFacet INFO = new BordersInfoFacet(this);
    final BorderLogic logic = new BorderLogic(this, new DefaultBorderRules());

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------
    final List<UUID> borderPath = new ArrayList<>();
    private final List<Border> borders;

    // ---------------------------------------------------------------------
    // Authority
    // ---------------------------------------------------------------------

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
            borders.add(Border.load(this, (CompoundTag) t));
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

    @Override
    public UUID authorityId() {
        return scope() != null
                ? scope().uuid()
                : new UUID(0L, 0L);
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

    // ------------------------------------------------------------------
    // New accept path (temporary)
    // ------------------------------------------------------------------
    Border accept(BorderProposal proposal) {
        requireServerSide();

        Border border = proposal.create(this);

        borders.removeIf(b -> b.id().equals(border.id()));
        borders.add(border);

        markDirty();
        return border;    // if applicable
    }
}
