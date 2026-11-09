package com.arryn.frontiermode.border.server.commands;

import com.arryn.frontiermode.core.MobSpawns;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.server.player.SicknessSwitch;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BorderDisplay;
import com.arryn.frontiermode.border.common.fixture.Result;
import com.arryn.frontiermode.border.server.BorderPathGrown;
import com.arryn.satchel.Satchel;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

@SuppressWarnings("SameReturnValue")
public final class BorderCommandHandler {

    private BorderCommandHandler() {
    }

    private static Component msg(String m) {
        return Component.literal("[Border] " + m);
    }

    /** The answer for a command reached before this level's Border facet is ready. */
    private static int notReady(CommandSourceStack source) {
        source.sendFailure(msg("Border data is not available yet: this level's Border scope is not ready."
                + " Try again in a moment."));
        return 0;
    }

    // ------------------------------------------------------------
    // ADD
    // ------------------------------------------------------------

    public static int addExplicit(
            CommandContext<CommandSourceStack> ctx,
            BlockPos pos,
            int radius,
            int layer
    ) {
        Level level = ctx.getSource().getLevel();

        // BorderAPI.addBorder now returns Result instead of throwing -- same rejection
        // (bad radius, negative layer), but read as data instead of an IllegalStateException.
        Result result = BorderAPI.addBorder(level, pos, radius, layer);
        if (!result.isSuccess()) {
            ctx.getSource().sendFailure(msg("Rejected: " + result.message()));
            return 0;
        }

        Border border = result.border();
        ctx.getSource().sendSuccess(
                () -> msg("Created border " + border.id()),
                false
        );
        return 1;
    }

    public static int addHere(
            CommandContext<CommandSourceStack> ctx,
            int radius,
            int layer
    ) throws CommandSyntaxException {

        Player sp = ctx.getSource().getPlayerOrException();

        Result result = BorderAPI.addBorder(
                sp.level(),
                sp.blockPosition(),
                radius,
                layer
        );
        if (!result.isSuccess()) {
            // See addExplicit()'s matching comment above.
            sp.sendSystemMessage(msg("Rejected: " + result.message()));
            return 0;
        }

        Border border = result.border();
        sp.sendSystemMessage(
                msg("Created border " + border.id() + " at your location")
        );
        return 1;
    }

    // ------------------------------------------------------------
    // DELETE
    // ------------------------------------------------------------

    public static int delete(
            CommandContext<CommandSourceStack> ctx,
            UUID id
    ) {
        // removeBorder now returns Result instead of boolean -- carries the real reason
        // (not-ready vs. not-found) instead of a single generic failure message.
        Result result = BorderAPI.removeBorder(ctx.getSource().getLevel(), id);

        if (!result.isSuccess()) {
            ctx.getSource().sendFailure(msg("Failed to remove border: " + result.message()));
            return 0;
        }

        ctx.getSource().sendSuccess(
                () -> msg("Removed border " + id),
                false
        );
        return 1;
    }

    // ------------------------------------------------------------
    // INFO
    // ------------------------------------------------------------

    public static int info(
            CommandContext<CommandSourceStack> ctx,
            UUID id
    ) {

        Level level = ctx.getSource().getLevel();

        BorderAPI.border(level, id).ifPresentOrElse(
                b -> {
                    // LEV is this border's position in the canonical path order, distinct from LAY
                    // (Border.layer()) -- the two are definitionally unrelated, so both are
                    // shown rather than conflated.
                    int pathIndex = BorderAPI.PATH(level)
                            .map(p -> p.indexOf(id))
                            .orElse(-1);
                    ctx.getSource().sendSuccess(
                            () -> msg(BorderDisplay.shortInfo(b, pathIndex)),
                            false
                    );
                },
                () -> ctx.getSource().sendFailure(
                        msg("Border not found: " + id)
                )
        );
        return 1;
    }

    // ------------------------------------------------------------
    // TRANSFORM
    // ------------------------------------------------------------

    public static int transform(
            CommandContext<CommandSourceStack> ctx,
            UUID id,
            BlockPos center,
            Integer radius
    ) {
        Result result = BorderAPI.transformBorder(
                ctx.getSource().getLevel(),
                id,
                center,
                radius
        );

        if (!result.isSuccess()) {
            // See BorderCommandHandler#addExplicit's matching comment -- same rejection path,
            // same fix.
            ctx.getSource().sendFailure(msg("Rejected: " + result.message()));
            return 0;
        }

        ctx.getSource().sendSuccess(
                () -> msg("Transformed border " + id),
                false
        );
        return 1;
    }

    // ------------------------------------------------------------
    // GROW
    // ------------------------------------------------------------

    public static int pathGrow(CommandContext<CommandSourceStack> ctx, BlockPos center) {

        Level level = ctx.getSource().getLevel();

        // No "if no borders exist, refuse to grow" guard here: BordersPathFacet.grow() already
        // branches internally on an empty path and bootstraps for exactly that case, which is how a
        // level's very first border gets created. Command and trigger behave the same way; an
        // absent tip isn't special for the explicit-center overload below either (see its own
        // doc).

        // BorderAPI.grow(Level) is deprecated: this command requires an explicit center (see
        // BorderCommands.path()'s "center" argument), and BorderAPI.grow(Level)'s own doc comment
        // names the one caller that still legitimately needs the no-center form.
        Result result = BorderAPI.grow(level, center);
        if (!result.isSuccess()) {
            // same rejection path as addExplicit()'s matching comment
            ctx.getSource().sendFailure(msg("Rejected: " + result.message()));
            return 0;
        }

        // pathGrow() is deliberately Keeper-less: the newly-grown border is announced on Satchel's bus,
        // and the Keeper's consumer marks it pending attach, so its reconciliation check reads the gap as a
        // sanctioned wait-state ("awaiting /boss attach"), not a real data bug, until an admin runs /boss attach
        // against it. Border imports neither Boss nor Keeper (border.md § Path grown). Only this command posts
        // the event, not BorderAPI.grow, which the Keeper's own defeat cascade calls too.
        Satchel.require().eventBus().post(new BorderPathGrown(level, result.border()));

        ctx.getSource().sendSuccess(
                () -> msg("Advanced border progression"),
                false
        );
        return 1;
    }



    public static int pathInsert(
            CommandContext<CommandSourceStack> ctx,
            BorderSelectorResult selector
    ) throws CommandSyntaxException {

        var source = ctx.getSource();
        var player = source.getPlayerOrException();
        var level = source.getLevel();

        Border border = BorderSelector.resolveSingle(selector, player);
        var pathOpt = BorderAPI.PATH(level);
        if (pathOpt.isEmpty()) {
            return notReady(source);
        }
        var path = pathOpt.get();

        // A border already on the path is the admin's mistake, so it is checked and refused here;
        // insert() throws for it.
        if (path.contains(border)) {
            source.sendFailure(msg("Cannot insert border into path: it is already on the path."));
            return 0;
        }
        path.insert(path.size(), border);

        source.sendSuccess(
                () -> msg("Inserted border into path."),
                false
        );
        return 1;
    }

    public static int pathRemove(
            CommandContext<CommandSourceStack> ctx,
            BorderSelectorResult selector
    ) throws CommandSyntaxException {

        var source = ctx.getSource();
        var player = source.getPlayerOrException();
        var level = source.getLevel();

        Border border = BorderSelector.resolveSingle(selector, player);

        var pathOpt = BorderAPI.PATH(level);
        if (pathOpt.isEmpty()) {
            return notReady(source);
        }
        var path = pathOpt.get();

        boolean removed = path.remove(border);

        if (!removed) {
            source.sendFailure(msg("Border is not present in the path."));
            return 0;
        }

        source.sendSuccess(
                () -> msg("Removed border from path."),
                false
        );
        return 1;
    }

    public static int pathMoveUp(
            CommandContext<CommandSourceStack> ctx,
            BorderSelectorResult selector
    ) throws CommandSyntaxException {

        var source = ctx.getSource();
        var player = source.getPlayerOrException();
        var level = source.getLevel();

        Border border = BorderSelector.resolveSingle(selector, player);
        var pathOpt = BorderAPI.PATH(level);
        if (pathOpt.isEmpty()) {
            return notReady(source);
        }
        var path = pathOpt.get();
        Result result = path.moveUp(border);
        if (!result.isSuccess()) {
            source.sendFailure(msg("Rejected: " + result.message()));
            return 0;
        }

        source.sendSuccess(
                () -> msg("Moved border up in path."),
                false
        );
        return 1;
    }

    public static int pathMoveDown(
            CommandContext<CommandSourceStack> ctx,
            BorderSelectorResult selector
    ) throws CommandSyntaxException {

        var source = ctx.getSource();
        var player = source.getPlayerOrException();
        var level = source.getLevel();

        Border border = BorderSelector.resolveSingle(selector, player);

        var pathOpt = BorderAPI.PATH(level);
        if (pathOpt.isEmpty()) {
            return notReady(source);
        }
        var path = pathOpt.get();
        Result result = path.moveDown(border);
        if (!result.isSuccess()) {
            source.sendFailure(msg("Rejected: " + result.message()));
            return 0;
        }

        source.sendSuccess(
                () -> msg("Moved border down in path."),
                false
        );
        return 1;
    }

    public static int pathFixLayers(
            CommandContext<CommandSourceStack> ctx
    ) {
        var level = ctx.getSource().getLevel();
        var pathOpt = BorderAPI.PATH(level);
        if (pathOpt.isEmpty()) {
            return notReady(ctx.getSource());
        }
        var path = pathOpt.get();

        // fixLayers() is the real reorder-to-match-path-order reconciliation -- reports the two
        // real outcomes distinctly (see Border Path & Layer Reconciliation, "Command-layer
        // behavior").
        int changedCount = path.fixLayers();

        if (changedCount == 0) {
            ctx.getSource().sendSuccess(
                    () -> msg("Path and layer order already match -- no changes made."),
                    false
            );
            return 0;
        }

        ctx.getSource().sendSuccess(
                () -> msg("Reconciled " + changedCount + " border layer(s) with path order."),
                false
        );
        return changedCount;
    }

    public static int debug(CommandContext<CommandSourceStack> ctx) {
        Level level = ctx.getSource().getLevel();

        var opt = BorderAPI.CRUD(level);

        if (opt.isEmpty()) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("[Border][Debug] Borders bundle: NOT PRESENT"),
                    false
            );
            return 0;
        }

        var borders = opt.get().all();

        ctx.getSource().sendSuccess(
                () -> Component.literal(
                        "[Border][Debug] Borders bundle present. Count = " + borders.size()
                ),
                false
        );

        return borders.size();
    }

    // ------------------------------------------------------------------
    // DEBUG SICKNESS -- project owner request. Global debug state (not per-player),
    // same "/boss debug glow" shape -- no retroactive-apply concern here though: unlike glow (a
    // synced entity flag set once), sickness is re-evaluated every refresh interval already, so
    // toggling the switch just changes what the very next refresh does.
    // ------------------------------------------------------------------
    public static int setSicknessEnabled(CommandContext<CommandSourceStack> ctx, boolean value) {
        if (!SicknessSwitch.set(ctx.getSource().getServer(), value)) {
            ctx.getSource().sendFailure(msg("[Border][Debug] The sickness switch is not available yet: the"
                    + " Overworld's Border scope is not ready. Try again in a moment."));
            return 0;
        }

        ctx.getSource().sendSuccess(
                () -> msg("[Border][Debug] Frontier Sickness " + (value ? "enabled" : "disabled")
                        + ". Already-granted effects on players still outside will keep running "
                        + "down on their own vanilla duration rather than being cleared instantly."),
                false
        );

        return 1;
    }

    // ------------------------------------------------------------------
    // Test aid: unlike /summon (which loads a raw entity from NBT and never
    // touches Mob#finalizeSpawn at all), this spawns each rabbit with DISPATCHED setup -- the real
    // Forge finalize-spawn event, which SpawnIngress translates into
    // MobSpawnFinalizing, which SpawnCoordinator fans out to SickWildlife
    // (Sick Wildlife's density hook) and the other modules. A deterministic way to exercise that hook on
    // demand instead of waiting on natural spawn RNG.
    // ------------------------------------------------------------------
    private static final RandomSource DEBUG_RNG = RandomSource.create();

    public static int spawnTestAnimals(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        Player player = ctx.getSource().getPlayerOrException();
        ServerLevel level = ctx.getSource().getLevel();

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            int dx = DEBUG_RNG.nextInt(29) - 14;
            int dz = DEBUG_RNG.nextInt(29) - 14;
            BlockPos ground = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    player.blockPosition().offset(dx, 0, dz));

            if (MobSpawns.spawn(level, EntityType.RABBIT, ground, MobSpawns.Setup.DISPATCHED).isPresent()) {
                spawned++;
            }
        }

        int finalSpawned = spawned;
        ctx.getSource().sendSuccess(
                () -> Component.literal(
                        "[Border][Debug] Spawned " + finalSpawned + "/" + count
                                + " test rabbits via a real FinalizeSpawn event (unlike /summon)."
                ),
                false
        );

        return spawned;
    }

}
