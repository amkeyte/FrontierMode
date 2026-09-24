package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.satchel.common.fixture.SatchelFixture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FRO_145: the client-syncable "this entity is a boss" signal -- a flat collection of
 * {@code {bossId, mobUuid, alive, scale}} records, one per currently-attached boss entity (see boss.md's
 * "Client-side visual marker").
 *
 * <p><b>Deliberately carries no position.</b> The marker only needs "is this entity a boss";
 * boss location stays server-only ({@code BOSS_JIG}'s secrecy policy, FRO_093), so this fixture
 * can be synced to every client in the dimension without leaking it.
 *
 * <p>Written server-side only, at the same {@code MobGainedInterest}/{@code MobLostInterest}
 * hooks {@link BossMobFixture} attaches/releases on -- real lifecycle tracking, not accumulate-
 * forever. Read client-side by the eye render layer via {@link #isLiveBoss(UUID)}; the client's
 * copy is hydrated from Satchel's periodic parcel ({@code hydrate()} below), never written by
 * hand.
 */
public final class BossPublicFixture extends SatchelFixture {

    private static final String KEY_MARKERS = "markers";

    private record Marker(UUID bossId, UUID mobUuid, boolean alive, float scale) {
    }

    // Keyed by mobUuid -- the render layer's lookup key, and unique per live entity.
    private final Map<UUID, Marker> markers = new HashMap<>();

    public BossPublicFixture() {
        registerCustom(KEY_MARKERS, this::saveMarkers, this::loadMarkers);
    }

    private void saveMarkers(CompoundTag root) {
        ListTag list = new ListTag();
        for (Marker m : markers.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("bossId", m.bossId);
            tag.putUUID("mobUuid", m.mobUuid);
            tag.putBoolean("alive", m.alive);
            tag.putFloat("scale", m.scale);
            list.add(tag);
        }
        root.put(KEY_MARKERS, list);
    }

    private void loadMarkers(CompoundTag root) {
        markers.clear();
        if (!root.contains(KEY_MARKERS, Tag.TAG_LIST)) {
            return;
        }
        for (Tag t : root.getList(KEY_MARKERS, Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) t;
            if (!tag.hasUUID("mobUuid") || !tag.hasUUID("bossId")) {
                continue;
            }
            UUID mobUuid = tag.getUUID("mobUuid");
            // "scale" is absent on a marker saved before FRO_148 -- 1.0 (unscaled) is the right
            // default, and the next attach rewrites it.
            float scale = tag.contains("scale", Tag.TAG_FLOAT) ? tag.getFloat("scale") : 1.0F;
            markers.put(mobUuid, new Marker(tag.getUUID("bossId"), mobUuid, tag.getBoolean("alive"), scale));
        }
    }

    // ── server-side writes ────────────────────────────────────────────────────

    /**
     * Adds (or refreshes) the marker for a just-attached boss entity. {@code scale} is the
     * boss's size multiplier (FRO_148), computed once server-side from its layer -- carried here so
     * the client's hitbox and render scale come from the same synced value, rather than a data
     * accessor bolted onto a vanilla entity class.
     */
    public void add(UUID bossId, UUID mobUuid, float scale) {
        markers.put(mobUuid, new Marker(bossId, mobUuid, true, scale));
        markDirty();
    }

    /** Removes the marker for a released boss entity. No-op if it was never added. */
    public void removeByMob(UUID mobUuid) {
        if (markers.remove(mobUuid) != null) {
            markDirty();
        }
    }

    /** Flags a marker not-alive (the entity is dying) ahead of its release. No-op if absent. */
    public void markDead(UUID mobUuid) {
        Marker m = markers.get(mobUuid);
        if (m != null && m.alive) {
            markers.put(mobUuid, new Marker(m.bossId, mobUuid, false, m.scale));
            markDirty();
        }
    }

    // ── reads (either side) ───────────────────────────────────────────────────

    /** True if {@code mobUuid} is a currently-tracked, alive boss entity. */
    public boolean isLiveBoss(UUID mobUuid) {
        Marker m = markers.get(mobUuid);
        return m != null && m.alive;
    }

    /** The boss's size multiplier (FRO_148), or {@code 1.0} if {@code mobUuid} isn't tracked. */
    public float scaleOf(UUID mobUuid) {
        Marker m = markers.get(mobUuid);
        return m == null ? 1.0F : m.scale;
    }

    /** Snapshot of every tracked boss entity's UUID -- diagnostics/tests. */
    public List<UUID> mobUuids() {
        return new ArrayList<>(markers.keySet());
    }
}
