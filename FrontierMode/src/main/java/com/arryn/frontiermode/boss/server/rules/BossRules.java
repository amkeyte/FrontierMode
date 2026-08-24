package com.arryn.frontiermode.boss.server.rules;

import com.arryn.frontiermode.border.common.fixture.Border;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Strategy interface for Boss's own two genuinely separate mechanisms -- position and
 * materialization -- mirroring {@code BorderRules}/{@code DefaultBorderRules}' existing
 * "safe baseline, replace later" shape. See wiki/frontiermode/architecture/boss.md's "Spawn
 * algorithm" section: these are not one fused loop.
 */
public interface BossRules {

    /**
     * Position -- picked once, immediately, at record-creation time. A uniform random XZ column
     * within {@code border}'s current disk. Has nothing to do with chunk loading; Y is left as a
     * placeholder (see {@code BossRecord}'s own docs).
     */
    BlockPos choosePosition(Level level, Border border);

    /**
     * Materialization -- called only once the caller (BOSS_JIG's own tick) has already confirmed
     * {@code Level.isLoaded(xz)}. Resolves a real ground Y at {@code xz}, spawns the vanilla mob
     * for {@code layer}, applies placeholder stat scaling, and attaches a visible marker so "no
     * discovery aids" (Tier 1) still means "findable by looking." Returns empty if a valid ground
     * position or a legal spawn couldn't be resolved (e.g. the column is all liquid/void, or the
     * entity failed to add) -- the caller retries next tick, same fixed {@code xz}, never a new
     * random guess.
     *
     * <p>Deliberately does not call {@code MobScope.getFor(mob)} or touch {@code BossFixture} --
     * both are Satchel-wiring concerns owned by {@code BossModule}, not a "boss design" decision.
     */
    Optional<Mob> materialize(ServerLevel level, BlockPos xz, int layer);
}
