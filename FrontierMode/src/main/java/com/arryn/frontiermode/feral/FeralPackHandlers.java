package com.arryn.frontiermode.feral;

import com.arryn.frontiermode.feral.common.fixture.FeralPackFixture;
import com.arryn.satchel.common.lifecycle.MobDied;
import com.arryn.satchel.common.lifecycle.MobRemoved;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pack-member records ({@link FeralPackFixture}; FRO_201): marking a member at spawn, reading
 * membership, and deleting a record when its mob is gone for good. A member unloaded with its chunk
 * keeps its record -- it's still a pack member when it loads back.
 */
final class FeralPackHandlers {

    /**
     * Per-level interest for {@code FERAL_PACK_MOB_JIG}: each level's set is the live view of its
     * fixture's members, not a second set. Registered from Feral's level tick; nothing is rebuilt
     * per call, since {@code MobDied}'s gate walks every supplier on every mob death.
     */
    static final Map<Level, Set<UUID>> INTERESTS = new ConcurrentHashMap<>();

    private FeralPackHandlers() {
    }

    /** Feral's level tick: keeps this level's interest pointing at its fixture's live member view. */
    static void onLevelTick(ServerLevel level) {
        FeralPackFixture.of(level).ifPresent(pack -> INTERESTS.put(level, pack.memberIds()));
    }

    /** Records {@code mob} as a pack member. Called before it joins the level. */
    static void markMember(ServerLevel level, Mob mob) {
        FeralPackFixture.of(level).ifPresent(pack -> pack.add(mob.getUUID()));
    }

    /** Drops a record for a mob that never made it into the level. */
    static void forget(ServerLevel level, UUID mobUuid) {
        FeralPackFixture.of(level).ifPresent(pack -> pack.remove(mobUuid));
    }

    /**
     * This level's pack members, for a scan that checks many mobs: resolve once, then test
     * {@code contains}. Empty while the fixture isn't resolvable, so nothing counts as a member then.
     */
    static Set<UUID> members(ServerLevel level) {
        return FeralPackFixture.of(level).map(FeralPackFixture::memberIds).orElse(Set.of());
    }

    /**
     * {@code MobRemoved} is posted unscoped and only for an interest-registered mob: a member that
     * left the level for good (killed, discarded, changed dimension) loses its record.
     */
    static void onMobRemoved(MobRemoved event) {
        if (event.isGone() && event.level() instanceof ServerLevel level) {
            forget(level, event.uuid());
        }
    }

    /** {@code MobDied} is posted unscoped: a no-op unless the dying mob is a pack member. */
    static void onMobDied(MobDied event) {
        if (event.level() instanceof ServerLevel level) {
            forget(level, event.uuid());
        }
    }
}
