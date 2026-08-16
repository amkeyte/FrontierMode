package com.arryn.satchel.common.persistence;

import com.arryn.satchel.common.util.out.OUT;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * RM_SAT_019 — persisted per-world identity token.
 *
 * <p>
 * One record per world, always anchored on the overworld's {@code DataStorage} (see
 * {@code storageId}) regardless of which dimension triggers the lookup — a token is a property of
 * the *world*, not of any one dimension within it, and the overworld is the one dimension every
 * vanilla world is guaranteed to have.
 *
 * <p>
 * Deliberately minimal, following {@link BundleSavedData}'s shape: private constructor,
 * {@code createNew}/{@code loadExisting} static factories, {@code getForRead}/{@code getForWrite}
 * entry points. Unlike {@code BundleSavedData} there is no per-record key to validate on load —
 * there is exactly one of these per world, so a mismatch isn't a meaningful failure mode here.
 *
 * <p>
 * This class is Forge / server-only by design — see {@code WorldIdentityContext} for the
 * side-bound in-memory holder both sides actually read from at runtime.
 */
public final class WorldIdentitySavedData extends SavedData {

    private static final String STORAGE_ID = "satchel_world_identity";
    private static final String KEY_TOKEN = "world.identity.token";

    private final UUID token;

    private WorldIdentitySavedData(UUID token) {
        this.token = token;
    }

    private static WorldIdentitySavedData createNew() {
        UUID generated = UUID.randomUUID();
        OUT.TRACE().log("[WorldIdentitySavedData] Generating new world-identity token: " + generated);
        WorldIdentitySavedData data = new WorldIdentitySavedData(generated);
        // Newly generated — must be written to disk at least once, unlike a freshly loaded
        // existing record which is already on disk and doesn't need an immediate re-save.
        data.setDirty();
        return data;
    }

    private static WorldIdentitySavedData loadExisting(CompoundTag tag) {
        UUID stored = tag.getUUID(KEY_TOKEN);
        OUT.TRACE().log("[WorldIdentitySavedData] Loaded existing world-identity token: " + stored);
        return new WorldIdentitySavedData(stored);
    }

    public UUID token() {
        return token;
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag) {
        tag.putUUID(KEY_TOKEN, token);
        return tag;
    }

    /**
     * Writable access. Creates (and immediately marks dirty, so it's actually persisted) a new
     * token record if one does not yet exist. Always call this against the overworld's
     * {@link ServerLevel} — see class docs.
     */
    public static WorldIdentitySavedData getForWrite(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                WorldIdentitySavedData::loadExisting,
                WorldIdentitySavedData::createNew,
                STORAGE_ID
        );
    }

    @Override
    public String toString() {
        return "WorldIdentitySavedData[" + token + "]";
    }
}
