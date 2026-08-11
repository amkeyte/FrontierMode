package com.arryn.satchel.common.newstuff;

import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.util.out.OUT;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Satchel per-bundle persistence record.
 *
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Persist bundle identity (UUID + name)</li>
 *   <li>Persist a snapshot of fixture NBT provided by runtime bundles</li>
 * </ul>
 *
 * <p>
 * Explicitly does NOT:
 * <ul>
 *   <li>Hydrate runtime bundles</li>
 *   <li>Construct or register bundles</li>
 *   <li>Manage lifecycle transitions</li>
 *   <li>Inspect fixture semantics</li>
 * </ul>
 *
 * <p>
 * This class is Forge / server-only by design.
 * </p>
 */
public final class BundleSavedData extends SavedData {

    // ---------------------------------------------------------------------
    // NBT keys
    // ---------------------------------------------------------------------

    private static final String KEY_UUID = "bundle.key.uuid";
    private static final String KEY_NAME = "bundle.key.name";
    private static final String KEY_FIXTURES = "fixtures";

    // ---------------------------------------------------------------------
    // Identity
    // ---------------------------------------------------------------------

    private final BundleKey<?> key;

    /**
     * Snapshot of fixture data last projected by the runtime bundle.
     * This is not authoritative runtime state.
     */
    private CompoundTag fixtureSnapshot = new CompoundTag();

    // ---------------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------------

    private BundleSavedData(BundleKey<?> key) {
        if (key.isEmpty()) {
            throw new IllegalStateException(
                    "Generic bundle key UUID " + key.id
            );
        }

        this.key = Objects.requireNonNull(key, "key");
    }

    private static BundleSavedData createNew(BundleKey<?> key) {
        OUT.TRACE().log("[BundleSavedData] Creating new record for " + key);
        return new BundleSavedData(key);
    }

    // ---------------------------------------------------------------------
    // Raw load (ingress support)
    // ---------------------------------------------------------------------

    private static BundleSavedData loadExisting(
            BundleKey<?> key,
            CompoundTag tag
    ) {
        OUT.TRACE().log("[BundleSavedData] Reading existing record for " + key);

        UUID storedId = tag.getUUID(KEY_UUID);
        String storedName = tag.getString(KEY_NAME);

        if (!storedId.equals(key.id)) {
            throw new IllegalStateException(
                    "BundleKey UUID mismatch during load. Stored=" +
                            storedId + ", Runtime=" + key.id
            );
        }

        if (!storedName.equals(key.name)) {
            throw new IllegalStateException(
                    "BundleKey name mismatch during load. Stored=\"" +
                            storedName + "\", Runtime=\"" + key.name + "\""
            );
        }

        BundleSavedData data = new BundleSavedData(key);

        if (tag.contains(KEY_FIXTURES)) {
            data.fixtureSnapshot =
                    tag.getCompound(KEY_FIXTURES).copy();
        }

        return data;
    }

    /**
     * Returns a defensive copy of the persisted fixture snapshot.
     */
    public CompoundTag getFixtureSnapshot() {
        return fixtureSnapshot.copy();
    }

    // ---------------------------------------------------------------------
    // Egress API (engine-controlled)
    // ---------------------------------------------------------------------

    /**
     * Replace the persisted fixture snapshot.
     *
     * <p>
     * Caller is responsible for marking this record dirty.
     * </p>
     */
    public void setFixtures(CompoundTag snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "Fixture snapshot must not be null"
            );
        }
        this.fixtureSnapshot = snapshot.copy();
    }

    /**
     * Mark this persistence record dirty so Minecraft will save it.
     */
    public void markBundleDirty() {
        this.setDirty();
    }

    // ---------------------------------------------------------------------
    // Save to disk
    // ---------------------------------------------------------------------

    @Override
    public @NotNull CompoundTag save(CompoundTag tag) {
        tag.putUUID(KEY_UUID, key.id);
        tag.putString(KEY_NAME, key.name);
        tag.put(KEY_FIXTURES, fixtureSnapshot);
        return tag;
    }

    // ---------------------------------------------------------------------
    // Utilities (server engine-facing)
    // ---------------------------------------------------------------------

    /**
     * Read-only access. Does NOT create a new record.
     */
    public static BundleSavedData getForRead(
            ServerLevel level,
            BundleKey<?> key
    ) {
        return level.getDataStorage().get(
                tag -> loadExisting(key, tag),
                storageIdFor(key)
        );
    }

    /**
     * Writable access. Creates a new record if one does not yet exist.
     */
    public static BundleSavedData getForWrite(
            ServerLevel level,
            BundleKey<?> key
    ) {
        return level.getDataStorage().computeIfAbsent(
                tag -> loadExisting(key, tag),
                () -> createNew(key),
                storageIdFor(key)
        );
    }

    private static String storageIdFor(BundleKey<?> key) {
        return "satchel_bundle_" + key.id;
    }

    @Override
    public String toString() {
        return "BundleSavedData[" + key + "]";
    }
}
