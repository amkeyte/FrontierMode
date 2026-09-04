package com.arryn.frontiermode.boss.common.fixture;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * FRO_081: Boss's record-lifecycle/mutation facet, mirroring {@code BordersCrudFacet}'s shape --
 * record creation, single-record lookup, position finalization, materialization, defeat, and
 * removal. A thin pass-through onto {@link BossFixture}'s own methods: unlike
 * {@code BordersFixture} (whose mutating methods are package-private, reached only through its
 * facets), {@link BossFixture}'s methods stay {@code public} -- {@code BossCommandHandler} and
 * {@code BossSelector} already hold direct {@link BossFixture} references from
 * {@code BossAPI.bosses(Level)} pre-dating this refactor, and narrowing that surface is a
 * separate encapsulation ticket (the shape {@code BordersFixture}'s own FRO_047 was), not this
 * one. This facet exists so {@code BossModule}'s own orchestration and {@code BossAPI}'s facade
 * route through one explicit CRUD surface instead of reimplementing/duplicating this logic
 * inline -- each method here forwards straight to the identically-named {@link BossFixture}
 * method, which already guards {@code requireServerSide()} and its own validation/
 * {@code markDirty()} itself.
 */
public class BossCrudFacet {
    private final BossFixture fixture;

    BossCrudFacet(BossFixture fixture) {
        this.fixture = fixture;
    }

    /**
     * See {@link BossFixture#create(int)}.
     */
    public Optional<BossRecord> create(int layer) {
        return fixture.create(layer);
    }

    /**
     * FRO_082: on-path creation, paired with real border-growth -- see
     * {@link BossFixture#create(int, UUID)}.
     */
    public Optional<BossRecord> create(int layer, UUID borderId) {
        return fixture.create(layer, borderId);
    }

    /**
     * See {@link BossFixture#get(UUID)}.
     */
    public Optional<BossRecord> get(UUID bossId) {
        return fixture.get(bossId);
    }

    /**
     * See {@link BossFixture#finalizePosition(UUID, BlockPos)}.
     */
    public boolean finalizePosition(UUID bossId, BlockPos resolvedPosition) {
        return fixture.finalizePosition(bossId, resolvedPosition);
    }

    /**
     * See {@link BossFixture#materialize(UUID, UUID)}.
     */
    public boolean materialize(UUID bossId, UUID entityId) {
        return fixture.materialize(bossId, entityId);
    }

    /**
     * See {@link BossFixture#markDefeated(UUID)}.
     */
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

    /**
     * FRO_082: see {@link BossFixture#addPendingAttach(UUID)}.
     */
    public boolean addPendingAttach(UUID borderId) {
        return fixture.addPendingAttach(borderId);
    }

    /**
     * FRO_082: see {@link BossFixture#removePendingAttach(UUID)}.
     */
    public boolean removePendingAttach(UUID borderId) {
        return fixture.removePendingAttach(borderId);
    }
}
