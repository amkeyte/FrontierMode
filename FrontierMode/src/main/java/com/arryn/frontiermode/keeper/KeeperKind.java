package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.boss.common.BossCreation;
import com.arryn.frontiermode.boss.common.BossKind;
import com.arryn.frontiermode.boss.common.fixture.BossRulesFacet;
import com.arryn.frontiermode.keeper.common.fixture.KeeperCreation;
import com.arryn.frontiermode.keeper.common.fixture.KeeperFixture;
import com.arryn.frontiermode.keeper.common.fixture.KeeperRecord;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * The Keeper's {@link BossKind} (wiki/frontiermode/architecture/keeper.md): key {@value #KEY}. It writes and
 * drops the Keeper's own {@link KeeperRecord}, and answers whether an entry is whole.
 */
public final class KeeperKind implements BossKind {

    /** The kind key a Keeper's base record names. */
    public static final String KEY = "keeper";

    @Override
    public String key() {
        return KEY;
    }

    /**
     * Writes the {@link KeeperRecord} for {@code bossId}, with the body chosen once here so a Keeper that
     * respawns after a non-player death keeps it. Refuses {@code layer < 1}: a Border may hold layer 0, a
     * Keeper may not.
     */
    @Override
    public boolean onCreate(Level level, UUID bossId, BossCreation args) {
        if (!(args instanceof KeeperCreation creation)) {
            throw new IllegalArgumentException("[Keeper] onCreate(): expected KeeperCreation, got "
                    + (args == null ? "null" : args.getClass().getName()));
        }
        if (creation.layer() < 1) {
            OUT.warn("[Keeper] onCreate(): rejected -- layer " + creation.layer()
                    + " is below 1; layers start at 1.");
            return false;
        }

        Optional<KeeperFixture> keepersOpt = KeeperAPI.keepers(level);
        Optional<BossRulesFacet> rulesOpt = BossAPI.RULES(level);
        if (keepersOpt.isEmpty() || rulesOpt.isEmpty()) {
            OUT.warn("[Keeper] onCreate(): KeeperFixture or boss rules not available for level "
                    + level.dimension().location() + " -- no Keeper record written for bossId="
                    + Ids.shortId(bossId));
            return false;
        }

        String body = rulesOpt.get().chooseBody(creation.pathPosition().orElse(creation.layer()));
        keepersOpt.get().add(new KeeperRecord(
                bossId, creation.layer(), creation.pathPosition(), body, creation.borderId(), null));
        return true;
    }

    @Override
    public boolean isComplete(Level level, UUID bossId) {
        return KeeperAPI.keepers(level).map(keepers -> keepers.get(bossId).isPresent()).orElse(false);
    }

    /**
     * Drops the {@link KeeperRecord} and, when the removed Keeper's Border is still on the path, marks it
     * pending attach: the same sanctioned wait-state a boss-less {@code pathGrow()} produces. A hand-placed
     * off-path Keeper (no {@code borderId}) skips that step, and so does a Border removed from the path some
     * other way, which shouldn't be queued for an attach that no longer makes sense.
     */
    @Override
    public void onRemoved(Level level, UUID bossId) {
        Optional<KeeperFixture> keepersOpt = KeeperAPI.keepers(level);
        if (keepersOpt.isEmpty()) {
            OUT.warn("[Keeper] onRemoved(): KeeperFixture not available for level " + level.dimension().location()
                    + " -- the Keeper record for bossId=" + Ids.shortId(bossId) + " is not dropped.");
            return;
        }

        KeeperFixture keepers = keepersOpt.get();
        keepers.remove(bossId).flatMap(KeeperRecord::borderId).ifPresent(borderId -> {
            if (isOnPath(level, borderId)) {
                keepers.addPendingAttach(borderId);
            }
        });
    }

    private static boolean isOnPath(Level level, UUID borderId) {
        return BorderAPI.PATH(level).map(path -> path.all().contains(borderId)).orElse(false);
    }
}
