package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.satchel.common.fixture.SatchelFixture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The client-visible sentinel record: {@code {mobUuid, scale}} for every sentinel currently
 * present in the level. Carries no position and no {@code bossId} -- sentinels cluster around
 * their boss, so a client able to pair one with the other would learn more than the trail is meant
 * to give away. Same shape as {@link BossPublicFixture}; see sentinels.md's
 * "SentinelPublicFixture".
 *
 * <p>Written server-side at {@code SENTINEL_MOB_JIG}'s interest hooks; the client's copy hydrates
 * from Satchel's parcel. Networked, not persisted (see {@code SentinelPublicBundle}).
 *
 * <p>A change forces a network push, coalesced to at most one per tick: writers set a pending flag
 * and this fixture's own tick flushes it, so a burst of sentinels joining in one tick costs one push
 * and a sentinel still reaches clients with its scale when it spawns, not a sync interval later.
 */
public final class SentinelPublicFixture extends SatchelFixture {

    private static final String KEY_SENTINELS = "sentinels";

    private record Entry(UUID mobUuid, float scale) {
    }

    private final Map<UUID, Entry> entries = new HashMap<>();
    private boolean pushPending;

    // The trail's tuning: level-wide fields the server copies in from its Tunables. The defaults are
    // what a client uses until the first parcel lands.
    private static final String KEY_TRAIL = "trail";
    private int trailIntervalTicks = 4;
    private int trailLifetimeTicks = 30;
    private int trailRadius = 24;

    public SentinelPublicFixture() {
        registerCustom(KEY_SENTINELS, this::saveEntries, this::loadEntries);
        registerCustom(KEY_TRAIL, this::saveTrail, this::loadTrail);
    }

    private void saveEntries(CompoundTag root) {
        ListTag list = new ListTag();
        for (Entry e : entries.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("mobUuid", e.mobUuid);
            tag.putFloat("scale", e.scale);
            list.add(tag);
        }
        root.put(KEY_SENTINELS, list);
    }

    private void loadEntries(CompoundTag root) {
        entries.clear();
        if (!root.contains(KEY_SENTINELS, Tag.TAG_LIST)) {
            return;
        }
        for (Tag t : root.getList(KEY_SENTINELS, Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) t;
            if (!tag.hasUUID("mobUuid")) {
                continue;
            }
            UUID mobUuid = tag.getUUID("mobUuid");
            entries.put(mobUuid, new Entry(mobUuid, tag.getFloat("scale")));
        }
    }

    private void saveTrail(CompoundTag root) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("interval", trailIntervalTicks);
        tag.putInt("lifetime", trailLifetimeTicks);
        tag.putInt("radius", trailRadius);
        root.put(KEY_TRAIL, tag);
    }

    private void loadTrail(CompoundTag root) {
        if (!root.contains(KEY_TRAIL, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag tag = root.getCompound(KEY_TRAIL);
        trailIntervalTicks = tag.getInt("interval");
        trailLifetimeTicks = tag.getInt("lifetime");
        trailRadius = tag.getInt("radius");
    }

    // ── server-side writes ────────────────────────────────────────────────────

    /** Adds the sentinel, or updates its scale. Idempotent: no change, no push. */
    public void add(UUID mobUuid, float scale) {
        Entry existing = entries.get(mobUuid);
        if (existing != null && existing.scale == scale) {
            return;
        }
        entries.put(mobUuid, new Entry(mobUuid, scale));
        changed();
    }

    /** Removes the sentinel. No-op if it was never added. */
    public void remove(UUID mobUuid) {
        if (entries.remove(mobUuid) != null) {
            changed();
        }
    }

    /** Copies the trail's tuning in; a push only when a value actually differs. */
    public void setTrail(int intervalTicks, int lifetimeTicks, int radius) {
        if (intervalTicks == trailIntervalTicks && lifetimeTicks == trailLifetimeTicks && radius == trailRadius) {
            return;
        }
        trailIntervalTicks = intervalTicks;
        trailLifetimeTicks = lifetimeTicks;
        trailRadius = radius;
        changed();
    }

    private void changed() {
        markDirty();
        pushPending = true;
    }

    /** Flushes a coalesced push. Server-side only in effect: a client-side sync is a no-op. */
    @Override
    public void onJigTick() {
        if (pushPending) {
            pushPending = false;
            getBundle().forceSync();
        }
    }

    // ── reads (either side) ───────────────────────────────────────────────────

    /** True if {@code mobUuid} is a sentinel currently present. */
    public boolean isSentinel(UUID mobUuid) {
        return entries.containsKey(mobUuid);
    }

    /** The sentinel's size multiplier, or {@code 1.0} if {@code mobUuid} isn't a known sentinel. */
    public float scaleOf(UUID mobUuid) {
        Entry e = entries.get(mobUuid);
        return e == null ? 1.0F : e.scale;
    }

    public int trailIntervalTicks() {
        return trailIntervalTicks;
    }

    public int trailLifetimeTicks() {
        return trailLifetimeTicks;
    }

    public int trailRadius() {
        return trailRadius;
    }
}
