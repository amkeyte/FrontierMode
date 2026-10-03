package com.arryn.frontiermode.feral.common.fixture;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.feral.common.bundle.FeralPackBundle;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelScope;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which Feral mobs in this level are pack members -- mobs {@code FeralModule}'s pack maintenance
 * spawned, as opposed to natural Wilds spawns that are Feral too. A Satchel record, not a tag on the
 * entity, the same shape as sentinel records: persisted, so a pack member is still one after its
 * chunk reloads or the server restarts, and deleted only when the mob is gone for good
 * ({@code FeralPackHandlers}). FRO_201.
 */
public final class FeralPackFixture extends SatchelFixture {

    private static final String KEY_MEMBERS = "members";

    // Concurrent so the MobJig interest walk can iterate the live view while a handler it triggers
    // removes a member.
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final Set<UUID> memberView = Collections.unmodifiableSet(members);

    public FeralPackFixture() {
        registerCustom(KEY_MEMBERS, this::saveMembers, this::loadMembers);
    }

    private void saveMembers(CompoundTag root) {
        ListTag list = new ListTag();
        for (UUID uuid : members) {
            list.add(NbtUtils.createUUID(uuid));
        }
        root.put(KEY_MEMBERS, list);
    }

    private void loadMembers(CompoundTag root) {
        members.clear();
        if (!root.contains(KEY_MEMBERS, Tag.TAG_LIST)) {
            return;
        }
        for (Tag t : root.getList(KEY_MEMBERS, Tag.TAG_INT_ARRAY)) {
            members.add(NbtUtils.loadUUID(t));
        }
    }

    public void add(UUID mobUuid) {
        if (members.add(mobUuid)) {
            markDirty();
        }
    }

    /** Idempotent and quiet: a death fires both {@code MobDied} and {@code MobRemoved}. */
    public void remove(UUID mobUuid) {
        if (members.remove(mobUuid)) {
            markDirty();
        }
    }

    public boolean contains(UUID mobUuid) {
        return members.contains(mobUuid);
    }

    /** A live, read-only view -- the pack MobJig's interest set. */
    public Set<UUID> memberIds() {
        return memberView;
    }

    /**
     * The level's pack records. Empty while Satchel or the scope isn't ready ("standby, don't
     * crash"); never logs, since the pack sweep asks every few ticks.
     */
    public static Optional<FeralPackFixture> of(Level level) {
        if (!Satchel.isReady()) {
            return Optional.empty();
        }
        LevelScope scope = new LevelScope(level);
        return Satchel.tryReadyScopeInfo(FrontierKeys.FERAL_LEVEL_JIG, scope).flatMap(info -> {
            LevelJig jig = (LevelJig) info.jigInfo().jig;
            FeralPackBundle bundle = jig.getOrCreate(scope, FrontierKeys.FERAL_PACK_BUNDLE);
            return bundle.pack();
        });
    }
}
