package com.arryn.satchel.common.lifecycle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;

import java.util.Objects;

/**
 * Posted to {@link SatchelEventBus} synchronously, off the real {@link BlockEvent.BreakEvent},
 * when a player breaks a block — genuinely same-call-stack, not discovered on a later tick poll,
 * so a consumer reacting to it (Cairn's capstone explosion) reads as an immediate reaction to the
 * player's own break.
 *
 * <p>
 * Non-cancelable in practice today — no consumer currently needs to veto a break — but goes
 * through the same uniform {@link SatchelSignal} base as {@link ItemPickupSignal} rather than a
 * separate, parallel one-off. {@link BlockEvent.BreakEvent} is itself {@code @Cancelable} in
 * Forge, so {@link #cancel()} would work if a future consumer needed it; this signal doesn't
 * decide that up front.
 *
 * <p>
 * {@code level}/{@code player} are typed as the side-agnostic {@link Level}/{@link Player}, not
 * {@code ServerLevel}/{@code ServerPlayer}, matching {@link MobDied}'s own common-code
 * convention — {@link BlockEvent.BreakEvent} only ever fires server-side in practice (see the
 * producer's own doc), but this signal's shape stays side-agnostic regardless; a consumer that
 * specifically needs the server types casts them itself.
 *
 * <p>
 * Posted once, unscoped, to the whole bus — every subscriber checks its own relevance before
 * acting, same discipline {@link MobDied} established.
 *
 * <p>
 * Producer: {@code ServerForgeIngress.onBlockBreak} (SAT_072).
 */
public final class BlockBreakSignal extends SatchelSignal<BlockEvent.BreakEvent> {

    private final Level level;
    private final BlockPos pos;
    private final BlockState state;
    private final Player player;

    public BlockBreakSignal(Level level, BlockEvent.BreakEvent forgeEvent) {
        super(forgeEvent);
        this.level = Objects.requireNonNull(level, "level");
        this.pos = Objects.requireNonNull(forgeEvent.getPos(), "pos");
        this.state = Objects.requireNonNull(forgeEvent.getState(), "state");
        this.player = Objects.requireNonNull(forgeEvent.getPlayer(), "player");
    }

    /** The level the block was broken in. */
    public Level level() {
        return level;
    }

    /** The broken block's position. */
    public BlockPos pos() {
        return pos;
    }

    /** The block state as it was immediately before breaking. */
    public BlockState state() {
        return state;
    }

    /** The player who broke the block. */
    public Player player() {
        return player;
    }
}
