package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.boss.server.rules.BossRules;
import com.arryn.frontiermode.boss.server.rules.DefaultBossRules;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.jig.guts.SatchelException;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Side-agnostic ingress API for interacting with Bosses -- mirrors {@code BorderAPI}'s shape and
 * "standby, don't crash" discipline exactly.
 */
public final class BossAPI {

    private BossAPI() {
    }

    private static final BossRules RULES = new DefaultBossRules();

    private static LogicalFoundation foundation() {
        return Satchel.require();
    }

    public static LevelJig levelJig() {
        return (LevelJig)
                foundation().requireJigInfo(FrontierKeys.BOSS_JIG).jig;
    }

    public static Optional<BossFixture> boss(Level level) {
        if (!Satchel.isReady()) {
            OUT.debug("[BossAPI] boss(): Satchel not ready yet -> Optional.empty level="
                    + level.dimension().location());
            return Optional.empty();
        }

        LevelScope scope = new LevelScope(level);

        var infoOpt = Satchel.require().tryScopeInfo(FrontierKeys.BOSS_JIG, scope);
        if (infoOpt.isEmpty()) {
            OUT.debug("[BossAPI] boss(): scope not yet known -> Optional.empty level="
                    + level.dimension().location());
            return Optional.empty();
        }

        var info = infoOpt.get();
        if (!info.isReady()) {
            OUT.debug("[BossAPI] boss(): scope NOT ready -> Optional.empty level="
                    + level.dimension().location() + " phase=" + info.phase());
            return Optional.empty();
        }

        try {
            return levelJig()
                    .getOrCreate(scope, FrontierKeys.BOSS_BUNDLE)
                    .get(FrontierKeys.BOSS);
        } catch (RuntimeException e) {
            throw new SatchelException.AccessFailed(
                    "Failed to resolve BossFixture for level " + level.dimension().location(), e);
        }
    }

    /**
     * Creates a new, unmaterialized boss record for {@code border} -- the direct call paired at a
     * real border-creation call site (see boss.md's "Defeat detection and the border-growth gap").
     * Position is picked here (via {@link BossRules#choosePosition}), immediately, with no
     * chunk-loaded check -- {@code layer} is copied once from {@code border.layer()} and never
     * re-read from {@code border} afterward.
     */
    public static Optional<BossRecord> createBoss(Level level, Border border) {
        Optional<BossFixture> fixtureOpt = boss(level);
        if (fixtureOpt.isEmpty()) {
            OUT.warn("[Boss] createBoss(): BossFixture not available for level "
                    + level.dimension().location() + " -- border " + border.id() + " gets no boss record.");
            return Optional.empty();
        }

        var position = RULES.choosePosition(level, border);
        return Optional.of(fixtureOpt.get().create(position, border.layer()));
    }
}
