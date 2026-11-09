package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.frontiermode.boss.common.BossCreation;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Boss's record-lifecycle/mutation facet, mirroring {@code BordersCrudFacet}'s shape --
 * record creation, single-record lookup, position finalization, materialization, defeat, and
 * removal. A thin pass-through onto {@link BossFixture}'s own methods: unlike
 * {@code BordersFixture} (whose mutating methods are package-private, reached only through its
 * facets), {@link BossFixture}'s methods stay {@code public} -- {@code BossCommandHandler} and
 * {@code BossSelector} already hold direct {@link BossFixture} references from
 * {@code BossAPI.bosses(Level)}, and narrowing that surface is a separate encapsulation ticket.
 * Each method here forwards straight to the identically-named {@link BossFixture} method, which
 * already guards {@code Satchel#requireServer()} and its own validation/{@code markDirty()} itself.
 */
public class BossCrudFacet {
    private final BossFixture fixture;

    BossCrudFacet(BossFixture fixture) {
        this.fixture = fixture;
    }

    /**
     * See {@link BossFixture#create(String, BossCreation)}.
     */
    public Optional<BossRecord> create(String kindKey, BossCreation args) {
        return fixture.create(kindKey, args);
    }

    /**
     * See {@link BossFixture#get(UUID)}.
     */
    public Optional<BossRecord> get(UUID bossId) {
        return fixture.get(bossId);
    }

    /**
     * See {@link BossFixture#finalizePosition(UUID, BlockPos)}.
     *
     * <p>[Arryn] examine why the Result value is never used.
     *
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean finalizePosition(UUID bossId, BlockPos resolvedPosition) {
        return fixture.finalizePosition(bossId, resolvedPosition);
    }

    /**
     * See {@link BossFixture#materialize(UUID, UUID, float)}.
     *
     * <p>[Arryn] examine why the Result value is never used.
     *
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean materialize(UUID bossId, UUID entityId, float scale) {
        return fixture.materialize(bossId, entityId, scale);
    }

    /**
     * See {@link BossFixture#dematerialize(UUID)}.
     */
    public boolean dematerialize(UUID bossId) {
        return fixture.dematerialize(bossId);
    }

    /**
     * See {@link BossFixture#markDefeated(UUID)}.
     */
    @SuppressWarnings({"inverted", "BooleanMethodIsAlwaysInverted"})
    public boolean markDefeated(UUID bossId) {
        return fixture.markDefeated(bossId);
    }

    /**
     * See {@link BossFixture#remove(UUID)}.
     */
    public boolean remove(UUID bossId) {
        return fixture.remove(bossId);
    }

    /**
     * See {@link BossFixture#all()}.
     */
    public List<BossRecord> all() {
        return fixture.all();
    }
}
