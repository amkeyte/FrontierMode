package com.arryn.frontiermode.boss.common.fixture;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.boss.common.bundle.BossMobBundle;
import com.arryn.frontiermode.boss.server.rules.DefaultBossRules;
import com.arryn.frontiermode.effects.common.BossBarHandle;
import com.arryn.frontiermode.effects.common.EffectsAPI;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.mob.MobJig;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code MobJig}-scoped, live view attached to a materialized boss entity itself -- the reverse
 * pointer ("which boss record does this entity belong to") that lets a future defeat handler
 * answer "is this dying entity a tracked boss" in O(1) from the entity alone, without scanning
 * every level's {@link BossFixture}. See wiki/frontiermode/architecture/boss.md's "Data model"
 * section.
 *
 * <p><b>Deliberately not persisted</b> -- cheap to rebuild from {@link BossFixture}'s own record
 * whenever a boss's entity happens to be present again, the same "cheap to recompute, don't
 * bother saving it" shape {@code BorderPlayerStatusFixture} already established in this codebase.
 * It exists only while the entity is confirmed live and disappears cleanly when it isn't -- no
 * data is ever lost by that, since it was never the authoritative copy of anything.
 *
 * <p>Also holds the boss's health bar (boss.md § Health bar): one per present boss, so a respawned
 * boss gets a fresh bar and several bosses each have their own. It goes away for everyone with this
 * fixture ({@link #onRemoved()}).
 */
public final class BossMobFixture extends SatchelFixture {

    private UUID bossId;

    // Created on the first viewer; null until then.
    private BossBarHandle healthBar;
    // Viewing player -> game time they last held the boss's kill credit.
    private final Map<UUID, Long> barViewers = new HashMap<>();

    // The boss's last-hurt timestamp as last seen, so each hit is noticed once (sentinels.md §
    // Defending the boss). Seeded at attach, so a hit from before it never counts.
    private int lastSeenHurtTimestamp;

    public BossMobFixture() {
    }

    /**
     * Set once, by the attach handler, at the moment {@code MobJig} confirms this entity is
     * present and this fixture is created for it (see {@code BossModule}'s
     * {@code ScopeEvent.Loaded} handler on {@code BOSS_MOB_JIG}).
     */
    public void attachTo(UUID bossId, Mob boss) {
        this.bossId = bossId;
        this.lastSeenHurtTimestamp = boss.getLastHurtByMobTimestamp();
    }

    public UUID bossId() {
        return bossId;
    }

    /**
     * One {@code BOSS_JIG} tick of the health bar (boss.md § Health bar). A player holding the boss's
     * kill credit -- their hit, projectile, or tamed pet -- starts watching; a viewer stops
     * {@code lingerTicks} after they last held it, or at once if they die, disconnect, or leave
     * {@code level}. A boss that is dead, or is not a {@code milestone} Keeper, shows no bar.
     */
    public void tickHealthBar(ServerLevel level, Mob boss, boolean milestone, int lingerTicks) {
        if (!boss.isAlive() || !milestone) {
            hideHealthBar();
            return;
        }

        long now = level.getGameTime();
        if (boss.getKillCredit() instanceof ServerPlayer credited) {
            if (healthBar == null) {
                healthBar = EffectsAPI.createBossBar(Component.literal(DefaultBossRules.BOSS_NAME),
                        BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
            }
            if (barViewers.put(credited.getUUID(), now) == null) {
                healthBar.addPlayer(credited);
                OUT.debug("[Boss] tickHealthBar(): showing bossId=" + Ids.shortId(bossId) + " to "
                        + credited.getGameProfile().getName());
            }
        }

        Iterator<Map.Entry<UUID, Long>> it = barViewers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> viewer = it.next();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(viewer.getKey());
            boolean stillWatching = player != null
                    && !player.isDeadOrDying()
                    && player.level() == level
                    && now - viewer.getValue() <= lingerTicks;
            if (!stillWatching) {
                healthBar.removePlayer(viewer.getKey());
                it.remove();
            }
        }

        if (healthBar != null) {
            healthBar.setProgress(boss.getHealth() / boss.getMaxHealth());
        }
    }

    /**
     * The player credited with a new hit on {@code boss} since this fixture last looked -- their hit,
     * projectile, or tamed pet, the same credit the health bar reads -- or empty. Answers once per
     * hit, not once per tick.
     */
    public Optional<ServerPlayer> takeNewPlayerHit(Mob boss) {
        int stamp = boss.getLastHurtByMobTimestamp();
        if (stamp == lastSeenHurtTimestamp) {
            return Optional.empty();
        }
        lastSeenHurtTimestamp = stamp;
        return boss.getKillCredit() instanceof ServerPlayer credited ? Optional.of(credited) : Optional.empty();
    }

    private void hideHealthBar() {
        if (healthBar != null) {
            healthBar.removeAll();
        }
        barViewers.clear();
    }

    /** The boss left (death, dismissal, unload): its bar goes with it, for everyone. */
    @Override
    public void onRemoved() {
        super.onRemoved();
        hideHealthBar();
    }

    /** This present boss's fixture, if {@code mob} is a tracked boss MobJig has attached. */
    public static Optional<BossMobFixture> of(Mob mob) {
        return existing(mob).filter(fixture -> fixture.bossId() != null);
    }

    /**
     * Resolves the dying entity's boss id, if it's a tracked boss. Checks the entity's existing
     * {@link BossMobFixture} first; if MobJig hasn't attached one yet, falls back to a
     * synchronous {@link MobScope#getFor(Mob)} call before concluding it's genuinely not tracked.
     * Safe to call on the death event since the entity is loaded by definition.
     */
    public static Optional<UUID> resolveBossId(Mob mob) {
        Optional<UUID> existing = existingBossId(mob);
        if (existing.isPresent()) {
            return existing;
        }
        MobScope.getFor(mob);
        return existingBossId(mob);
    }

    private static Optional<UUID> existingBossId(Mob mob) {
        return existing(mob).map(BossMobFixture::bossId);
    }

    private static Optional<BossMobFixture> existing(Mob mob) {
        if (!Satchel.isReady()) {
            return Optional.empty();
        }
        MobScope scope = new MobScope(mob);
        // FRO_150: Satchel.tryReadyScopeInfo() collapses the tryScopeInfo()/info.isReady() two
        // steps this used to hand-write into one call.
        Optional<ScopeInfo> infoOpt = Satchel.tryReadyScopeInfo(FrontierKeys.BOSS_MOB_JIG, scope);
        if (infoOpt.isEmpty()) {
            return Optional.empty();
        }
        var jig = (MobJig) infoOpt.get().jigInfo().jig;
        BossMobBundle bundle = jig.getOrCreate(scope, FrontierKeys.BOSS_MOB_BUNDLE);
        return Optional.of(bundle.getOrCreateFixture(FrontierKeys.BOSS_MOB, BossMobFixture::new));
    }
}