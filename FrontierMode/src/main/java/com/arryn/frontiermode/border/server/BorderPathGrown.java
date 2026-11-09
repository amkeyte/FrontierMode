package com.arryn.frontiermode.border.server;

import com.arryn.frontiermode.border.common.fixture.Border;
import net.minecraft.world.level.Level;

/**
 * The path has grown by one Border without a Keeper (wiki/frontiermode/architecture/border.md § Path grown). Posted on
 * Satchel's bus by {@code BorderCommandHandler.pathGrow} once the grow has succeeded, and by nothing else:
 * {@code BorderAPI.grow} does not post it, because the Keeper's own defeat cascade calls {@code grow} too and creates
 * its Keeper itself. Not a module event: it has no native Forge event. It runs synchronously on the server thread, in
 * the poster's call, after the state change it reports; the grow is already done and no consumer rolls it back.
 * Border imports neither Boss nor Keeper, so a module that must know registers its own consumer.
 *
 * @param level  the level the path grew in
 * @param border the new Border
 */
public record BorderPathGrown(Level level, Border border) {
}
