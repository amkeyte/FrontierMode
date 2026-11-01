package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BordersPathFacet;
import com.arryn.frontiermode.border.server.rules.BorderRules;
import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.keeper.common.fixture.KeeperCreation;
import com.arryn.frontiermode.keeper.common.fixture.KeeperFixture;
import com.arryn.frontiermode.keeper.common.fixture.KeeperRecord;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.SatchelException;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The Keeper's public surface (wiki/frontiermode/architecture/keeper.md § KeeperAPI), which the compass, the
 * Cairn, the Tell module, the Sentinel module and the Info report use instead of reaching into
 * {@code BossAPI} for Keeper concepts. Mirrors {@code BossAPI}'s "standby, don't crash" discipline.
 */
public final class KeeperAPI {

    private KeeperAPI() {
    }

    /**
     * The level's {@link KeeperFixture}, or empty when Satchel is not ready yet (the same "standby, don't
     * crash" entry point as {@code BossAPI.bosses}).
     */
    public static Optional<KeeperFixture> keepers(Level level) {
        if (!Satchel.isReady()) {
            OUT.debug("[Keeper] keepers(): Satchel not ready yet -> Optional.empty level="
                    + level.dimension().location());
            return Optional.empty();
        }

        LevelScope scope = new LevelScope(level);

        var infoOpt = Satchel.tryReadyScopeInfo(FrontierKeys.KEEPER_JIG, scope);
        if (infoOpt.isEmpty()) {
            OUT.debug("[Keeper] keepers(): scope not ready -> Optional.empty level="
                    + level.dimension().location());
            return Optional.empty();
        }

        try {
            LevelJig jig = (LevelJig) Satchel.require().requireJigInfo(FrontierKeys.KEEPER_JIG).jig;
            return jig.getOrCreate(scope, FrontierKeys.KEEPER_BUNDLE).get(FrontierKeys.KEEPER);
        } catch (RuntimeException e) {
            throw new SatchelException.AccessFailed(
                    "Failed to resolve KeeperFixture for level " + level.dimension().location(), e);
        }
    }

    /**
     * A Keeper's progression number -- what players are told is its "level" -- is its home Border's path
     * position, not its layer (keeper.md § The Keeper record). Falls back to {@code layer} for a Keeper with no
     * path position (hand-placed, or attached to an off-path Border), so it still has a number to show.
     */
    public static int pathPositionOf(KeeperRecord record) {
        return record.pathPosition().orElse(record.layer());
    }

    /**
     * {@code border}'s 1-based position on {@code level}'s path -- the value a Keeper record copies once at
     * creation. Empty if the Border is not on the path (or the path isn't resolvable yet).
     */
    public static OptionalInt pathPositionOf(Level level, Border border) {
        int index = BorderAPI.PATH(level).map(path -> path.indexOf(border.id())).orElse(-1);
        return index < 0 ? OptionalInt.empty() : OptionalInt.of(index + 1);
    }

    /**
     * Is {@code record} a milestone Keeper -- the one on the last Border of a growth wave (keeper.md §
     * Milestone Keepers)? Reads the record's own path position, never the layer fallback: a Keeper with no path
     * position closes no wave, so is never a milestone.
     */
    public static boolean isMilestone(KeeperRecord record) {
        if (record.pathPosition().isEmpty()) {
            return false;
        }
        BorderRules rules = BorderRules.ACTIVE;
        return rules.waveStep(record.pathPosition().getAsInt()) == rules.borderWaveLength();
    }

    /**
     * Resolves the Keeper at the level's current {@code BordersPathFacet} tip -- "Intuition/Resonance resolve
     * 'the current Path Tip' exactly once, at lock time, to get a boss identity" per special-compass.md's "One
     * shared core" section. A Border can carry more than one Keeper (themed multi-mob encounters share one
     * {@code borderId}); this returns any one alive match. Empty if the level has no path yet, no tip, or the
     * tip Border has no living Keeper.
     */
    public static Optional<UUID> pathTipBossId(Level level) {
        Optional<KeeperFixture> keepersOpt = keepers(level);
        Optional<BossFixture> bossesOpt = BossAPI.bosses(level);
        if (keepersOpt.isEmpty() || bossesOpt.isEmpty()) {
            return Optional.empty();
        }
        Optional<UUID> tipBorderId = BorderAPI.PATH(level).flatMap(BordersPathFacet::tip).map(Border::id);
        if (tipBorderId.isEmpty()) {
            return Optional.empty();
        }

        BossFixture bosses = bossesOpt.get();
        return keepersOpt.get().all().stream()
                .filter(k -> k.borderId().equals(tipBorderId))
                .map(KeeperRecord::bossId)
                .filter(id -> bosses.get(id).map(BossRecord::alive).orElse(false))
                .findFirst();
    }

    /**
     * Creates the Keeper for a progression {@code border}: the base {@code create("keeper", ...)}, whose kind
     * hook writes the {@code KeeperRecord}. {@code layer} and the path position are copied once from
     * {@code border} and never re-read. Returns empty on a rejection (a layer below 1) or when the boss data
     * is not resolvable yet, logged.
     */
    public static Optional<BossRecord> createBoss(Level level, Border border) {
        Optional<BossFixture> fixtureOpt = BossAPI.bosses(level);
        if (fixtureOpt.isEmpty()) {
            OUT.warn("[Keeper] createBoss(): BossFixture not available for level "
                    + level.dimension().location() + " -- border " + Ids.shortId(border.id())
                    + " gets no Keeper.");
            return Optional.empty();
        }

        return fixtureOpt.get().CRUD.create(KeeperKind.KEY,
                new KeeperCreation(border.layer(), Optional.of(border.id()), pathPositionOf(level, border)));
    }
}
