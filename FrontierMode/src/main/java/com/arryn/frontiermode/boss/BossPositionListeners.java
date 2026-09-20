package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * FRO_120 (Cairn): where a consumer registers to hear "a boss record's position just finalized"
 * without {@code BossModule}/{@code BossSpawnFacet} importing that consumer directly -- the same
 * one-directional-dependency discipline {@code MobInterestRegistry} already establishes for a
 * different concern (mob-death interest). Cairn's own record creation is paired with exactly this
 * moment (see wiki/frontiermode/architecture/cairn.md's "Data model" section: "the moment a
 * qualifying boss's own position commits, not raw boss-record creation"), but {@code
 * StructureModule} is a third module Boss has never heard of and never will -- {@code
 * StructureModule} depends on Boss (reads {@code BossAPI}/registers here), never the reverse,
 * mirroring {@code Feral}'s own one-directional relationship with Border/Boss.
 *
 * <p>Deliberately simpler than {@code MobInterestRegistry} -- not keyed by {@code JigKey}, since
 * there's only ever one thing to notify about ("boss position finalized"), not a per-consumer
 * interest set to look up. A plain list of listeners is the whole mechanism.
 */
public final class BossPositionListeners {

    private BossPositionListeners() {
    }

    private static final List<BiConsumer<Level, BossRecord>> LISTENERS =
            new CopyOnWriteArrayList<>();

    public static void register(BiConsumer<Level, BossRecord> listener) {
        LISTENERS.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Public only because the real caller ({@code BossSpawnFacet}) lives in a different package
     * (Java has no cross-package "friend" access) -- called from exactly one place,
     * {@code BossSpawnFacet.finalizeUnpositioned}, right after a position commits. Not meant as
     * a general-purpose entry point for other callers.
     */
    public static void fire(Level level, BossRecord record) {
        for (BiConsumer<Level, BossRecord> listener : LISTENERS) {
            listener.accept(level, record);
        }
    }
}
