package com.arryn.frontiermode.keeper;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BordersCrudFacet;
import com.arryn.frontiermode.border.common.fixture.BordersPathFacet;
import com.arryn.frontiermode.border.common.fixture.Result;
import com.arryn.frontiermode.boss.BodyScale;
import com.arryn.frontiermode.boss.Boss;
import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.boss.CreditTally;
import com.arryn.frontiermode.boss.HealthBar;
import com.arryn.frontiermode.boss.Leash;
import com.arryn.frontiermode.boss.SentinelJigHandlers;
import com.arryn.frontiermode.boss.common.BossCreation;
import com.arryn.frontiermode.boss.common.BossDeath;
import com.arryn.frontiermode.boss.common.BossKind;
import com.arryn.frontiermode.boss.common.DeathVerdict;
import com.arryn.frontiermode.boss.common.SiteOutcome;
import com.arryn.frontiermode.boss.common.Spawned;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.boss.common.fixture.BossRulesFacet;
import com.arryn.frontiermode.boss.common.fixture.BossSentinelsFixture;
import com.arryn.frontiermode.boss.common.fixture.BossTellFixture;
import com.arryn.frontiermode.keeper.common.BossPlatform;
import com.arryn.frontiermode.keeper.common.fixture.KeeperCreation;
import com.arryn.frontiermode.keeper.common.fixture.KeeperFixture;
import com.arryn.frontiermode.keeper.common.fixture.KeeperRecord;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.trinket.DynamicTrinket;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * The Keeper's {@link BossKind} (wiki/frontiermode/architecture/keeper.md): key {@value #KEY}. It writes and
 * drops the Keeper's own {@link KeeperRecord}, answers whether an entry is whole, and supplies the base
 * lifecycle's hooks: where the Keeper goes, how its site is prepared, what it spawns, whether a death is a
 * defeat, and what a defeat does.
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

    /**
     * The home Border by {@code layer}, once it exists and its pregeneration is ready, then the position
     * Siting scores best in it (keeper.md § Placement). Empty until then.
     */
    @Override
    public Optional<BlockPos> positionReady(Level level, UUID bossId) {
        Optional<KeeperRecord> keeperOpt = keeper(level, bossId);
        if (keeperOpt.isEmpty()) {
            return Optional.empty();
        }
        Optional<Border> borderOpt = resolveHomeBorder(level, keeperOpt.get().layer());
        if (borderOpt.isEmpty() || !BorderAPI.isPregenReady(level, borderOpt.get().id())) {
            return Optional.empty();
        }
        return Optional.of(rules(level).choosePosition(level, borderOpt.get()));
    }

    /**
     * Carves the Keeper's bedrock spawn platform, sized to its scale, so the entity never materializes over
     * nothing. Built once per record: a respawned Keeper returns onto the platform already there rather than
     * re-carving it, which would delete anything a player built around the spot. Bedrock isn't breakable in
     * survival. The caller has confirmed the chunk is loaded. It has never needed to wait.
     */
    @Override
    public SiteOutcome prepareSite(Level level, UUID bossId, BlockPos position) {
        Optional<KeeperFixture> keepersOpt = KeeperAPI.keepers(level);
        Optional<KeeperRecord> keeperOpt = keepersOpt.flatMap(keepers -> keepers.get(bossId));
        if (keeperOpt.isEmpty()) {
            return SiteOutcome.WAIT;
        }
        if (keeperOpt.get().platformBuilt()) {
            return SiteOutcome.READY;
        }
        BossPlatform.place(level, position, rules(level).keeperScale(keeperOpt.get()));
        keepersOpt.get().setPlatform(bossId, position.below());
        OUT.info("[Keeper] prepareSite(): built bedrock platform for bossId=" + Ids.shortId(bossId)
                + " centered at " + position.below());
        return SiteOutcome.READY;
    }

    /**
     * The Keeper's body, spawned with vanilla setup and then the Keeper's own steps, and its size
     * ({@code keeperScale}) for the base to store.
     */
    @Override
    public Optional<Spawned> spawn(Level level, UUID bossId, BlockPos position) {
        Optional<KeeperRecord> keeperOpt = keeper(level, bossId);
        if (keeperOpt.isEmpty()) {
            return Optional.empty();
        }
        BossRulesFacet rules = rules(level);
        KeeperRecord keeper = keeperOpt.get();
        return rules.materialize(level, position, keeper)
                .map(mob -> new Spawned(mob, rules.keeperScale(keeper)));
    }

    /**
     * A defeat needs a credited player, and the players' own damage must add up to {@code keeperPlayerShare}
     * of the Keeper's maximum health (FRO_215): allies, a raid, lava or another mob can finish a Keeper but
     * not carry the fight. Counted against maximum health, so a Keeper that healed simply needs more hits.
     */
    @Override
    public DeathVerdict judgeDeath(Level level, UUID bossId, BossDeath death) {
        if (!death.playerCredited()) {
            return DeathVerdict.LOSS;
        }
        double share = rules(level).keeperPlayerShare();
        if (death.playerDamage() < share * death.maxHealth()) {
            OUT.info("[Keeper] judgeDeath(): boss at " + death.position()
                    + " died with a player credited, but players dealt under " + share
                    + " of its maximum health -- counted as a non-player death.");
            return DeathVerdict.LOSS;
        }
        return DeathVerdict.DEFEAT;
    }

    /**
     * The Keeper's defeat cascade (keeper.md § Defeat): its Sentinels die with it, the defeat loot is paid at
     * the last-standing point, and an on-path Keeper that is the last standing on its Border grows the
     * Frontier from where it fell, creates the next Keeper and starts that Border's pregeneration and curves.
     * A hand-placed off-path Keeper (no {@code borderId}) grows nothing.
     */
    @Override
    public void onDefeat(Level level, UUID bossId, Mob mob, Optional<ServerPlayer> credited) {
        Optional<BossFixture> bossesOpt = BossAPI.bosses(level);
        Optional<KeeperRecord> keeperOpt = KeeperAPI.keepers(level).flatMap(keepers -> keepers.get(bossId));
        Optional<KeeperFixture> keepersOpt = KeeperAPI.keepers(level);
        if (bossesOpt.isEmpty() || keepersOpt.isEmpty() || keeperOpt.isEmpty()) {
            OUT.warn("[Keeper] onDefeat(): boss or Keeper data not available for level "
                    + level.dimension().location() + " -- nothing paid or grown for bossId=" + Ids.shortId(bossId));
            return;
        }
        BossFixture bosses = bossesOpt.get();
        KeeperFixture keepers = keepersOpt.get();
        KeeperRecord keeper = keeperOpt.get();

        // The boss's sentinels die with it, staggered; see sentinels.md's "When the boss is defeated".
        SentinelJigHandlers.onBossDefeated(level, bossId, credited.map(ServerPlayer::getUUID).orElse(null));

        // On-path gate: a hand-placed off-path boss never triggers growth. An on-path boss only
        // triggers growth once it's the last living sibling on that border.
        Optional<UUID> borderId = keeper.borderId();
        boolean lastStanding = borderId.isEmpty() || !keepers.anyAliveWithBorderId(bosses, borderId.get());

        // FRO_112: placeholder defeat loot -- pays out at the same last-standing point growth
        // itself gates on (trivially true for an off-path record), independent of whether growth
        // actually fires afterward.
        if (lastStanding && credited.isPresent()) {
            grantDefeatLoot(keeper, credited.get(), mob, level, bosses.RULES.keeperLootStep());
        }

        if (borderId.isEmpty() || !lastStanding) {
            return;
        }

        BlockPos deathLocation = mob.blockPosition();
        Result result = BorderAPI.grow(level, deathLocation);

        if (!result.isSuccess()) {
            // A boss that fell out of the world can die outside build-height range, making
            // grow(deathLocation) fail. Fall back to the boss's own recorded position, which is
            // always within build height by construction.
            BlockPos fallbackCenter = bosses.CRUD.get(bossId).map(BossRecord::position).orElse(null);
            if (fallbackCenter != null) {
                OUT.warn("[Keeper] onDefeat(): grow(" + deathLocation + ") failed for bossId="
                        + bossId + ": " + result.message()
                        + " -- retrying from recorded position " + fallbackCenter);
                result = BorderAPI.grow(level, fallbackCenter);
            }
        }

        if (!result.isSuccess()) {
            OUT.warn("[Keeper] onDefeat(): grow(" + deathLocation + ") failed for bossId="
                    + bossId + ": " + result.message() + " -- not calling createBoss.");
            return;
        }

        // FRO_233: tells a new player what just happened -- the Keeper fell and the frontier grew.
        // Server-wide, and only once the Border has actually grown. Names the Keeper's level, its path
        // position.
        credited.ifPresent(player -> player.server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.frontiermode.keeper_defeated", player.getDisplayName(),
                        KeeperAPI.pathPositionOf(keeper)), false));

        KeeperAPI.createBoss(level, result.border());
        BorderAPI.startPregeneration(level, result.border().id());
        BossTellFixture.createTellCurveIfAbsent(level, result.border().id());
        // RM_FRO_029 (Gloria): same paired-creation discipline as the Tell curve line above.
        BossSentinelsFixture.ensureSentinelCurves(level, result.border().id());
    }

    /**
     * The Keeper's mob-side abilities (keeper.md § Abilities). The hitbox follows the record's scale on both sides; the
     * rest are the server's: the damage tally, the leash (the first Keeper has its own, shorter radius), the health bar
     * for a milestone Keeper, and the Sentinels' defense.
     */
    @Override
    public void subscribeAbilities(Level level, Boss boss, DynamicTrinket host) {
        host.subscribe(new BodyScale());
        if (!Satchel.isServer()) {
            return;
        }
        Optional<KeeperRecord> keeperOpt = keeper(level, boss.bossId());
        if (keeperOpt.isEmpty()) {
            return;
        }
        KeeperRecord keeper = keeperOpt.get();
        BossRulesFacet rules = rules(level);

        host.subscribe(new CreditTally(boss));
        double radius = KeeperAPI.pathPositionOf(keeper) <= 1 ? rules.bossLeashRadiusFirst() : rules.bossLeashRadius();
        host.subscribe(new Leash(boss, radius));
        if (KeeperAPI.isMilestone(keeper)) {
            host.subscribe(new HealthBar(boss));
        }
        host.subscribe(new Defense(boss));
    }

    /**
     * The Keeper's defeat reward: vanilla-format loot tables rolled and dropped by {@link KeeperLoot}
     * (keeper.md § Defeat loot; FRO_213). Only reached for a player-credited defeat. FRO_134: a real item drop
     * at the death position, never a direct inventory insert, so it reads as a reward the player walks over
     * and picks up.
     */
    private static void grantDefeatLoot(KeeperRecord keeper, ServerPlayer credited, Mob mob, Level level,
                                        double lootStep) {
        OUT.debug("[Keeper] grantDefeatLoot(): paying " + credited.getGameProfile().getName()
                + " for bossId=" + Ids.shortId(keeper.bossId()));
        if (level instanceof ServerLevel serverLevel) {
            KeeperLoot.pay(serverLevel, mob, keeper, credited, lootStep);
        }
    }

    private static Optional<KeeperRecord> keeper(Level level, UUID bossId) {
        return KeeperAPI.keepers(level).flatMap(keepers -> keepers.get(bossId));
    }

    /** The boss rules, which exist whenever the roster that calls a hook does. */
    private static BossRulesFacet rules(Level level) {
        return BossAPI.RULES(level).orElseThrow(() -> new IllegalStateException(
                "[Keeper] rules(): boss rules not available for level " + level.dimension().location()));
    }

    private static Optional<Border> resolveHomeBorder(Level level, int layer) {
        Optional<BordersPathFacet> pathOpt = BorderAPI.PATH(level);
        Optional<BordersCrudFacet> crudOpt = BorderAPI.CRUD(level);
        if (pathOpt.isEmpty() || crudOpt.isEmpty()) {
            return Optional.empty();
        }
        BordersPathFacet path = pathOpt.get();
        BordersCrudFacet crud = crudOpt.get();

        for (UUID id : path.all()) {
            Optional<Border> borderOpt = crud.get(id);
            if (borderOpt.isPresent() && borderOpt.get().layer() == layer) {
                return borderOpt;
            }
        }
        return Optional.empty();
    }

    private static boolean isOnPath(Level level, UUID borderId) {
        return BorderAPI.PATH(level).map(path -> path.all().contains(borderId)).orElse(false);
    }
}
