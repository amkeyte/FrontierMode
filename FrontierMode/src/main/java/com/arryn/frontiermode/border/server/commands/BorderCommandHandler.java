package com.arryn.frontiermode.border.server.commands;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BorderDisplay;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

public final class BorderCommandHandler {

    private BorderCommandHandler() {
    }

    private static Component msg(String m) {
        return Component.literal("[Border] " + m);
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
        ServerLevel level = ctx.getSource().getLevel();

        Border border;
        try {
            border = BorderAPI.addBorder(level, pos, radius, layer);
        } catch (IllegalStateException e) {
            // RM_FRO_011's validation (BordersCrudFacet.applyProposal/validateProposal) throws a
            // raw IllegalStateException on rejection (bad radius, negative/colliding layer).
            // Uncaught, that surfaces to the player as Brigadier's generic "An unexpected error
            // occurred" -- confirmed from a real /border add ~ ~ ~ 999999999 0 test (FRO_023):
            // the server log showed a clean "[Border] Rejected proposal: radius ... outside
            // allowed range" WARN, but the player just saw the scary generic message instead of
            // that reason. This is the fix: same rejection, but the player actually sees why.
            ctx.getSource().sendFailure(msg("Rejected: " + e.getMessage()));
            return 0;
        }

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

        ServerPlayer sp = ctx.getSource().getPlayerOrException();

        Border border;
        try {
            border = BorderAPI.addBorder(
                    sp.serverLevel(),
                    sp.blockPosition(),
                    radius,
                    layer
            );
        } catch (IllegalStateException e) {
            // See addExplicit()'s matching comment above.
            sp.sendSystemMessage(msg("Rejected: " + e.getMessage()));
            return 0;
        }

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
        boolean removed =
                BorderAPI.removeBorder(ctx.getSource().getLevel(), id);

        if (!removed) {
            ctx.getSource().sendFailure(msg("Failed to remove border."));
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

        ServerLevel level = ctx.getSource().getLevel();

        BorderAPI.border(level, id).ifPresentOrElse(
                b -> {
                    // LEV is this border's position in the canonical path order, distinct from LAY
                    // (Border.layer()) -- see RM_FRO_015: the two are definitionally unrelated, so
                    // both are shown rather than conflating them the way the old "L:" label did.
                    int pathIndex = BorderAPI.borders(level)
                            .map(f -> f.PATH.indexOf(id))
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
        try {
            BorderAPI.transformBorder(
                    ctx.getSource().getLevel(),
                    id,
                    center,
                    radius
            );
        } catch (IllegalStateException e) {
            // See BorderCommandHandler#addExplicit's matching comment -- same rejection path,
            // same fix (RM_FRO_011 / FRO_023).
            ctx.getSource().sendFailure(msg("Rejected: " + e.getMessage()));
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

    public static int pathGrow(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {

        ServerLevel level = ctx.getSource().getLevel();

        // RM_FRO_015: removed an "if no borders exist, refuse to grow" guard that used to sit
        // here. It was backwards -- BordersPathFacet.grow() already branches internally on an
        // empty path (Optional<Border> tip absent) and calls BorderLogic.getInitial() for exactly
        // that case, which is precisely how a level's very first border is supposed to get
        // created. This guard intercepted that branch before it could ever run, so `/border path
        // grow` on a fresh level (0 borders) always failed with "No borders exist to grow." --
        // confirmed by real playtest: the gold-block trigger (BordersTriggers.growPath) calls
        // BorderAPI.grow(level) directly, has no such guard, and was never affected. Command and
        // trigger now behave the same way.

        try {
            BorderAPI.grow(level);
        } catch (IllegalStateException e) {
            // RM_FRO_015: same rejection path/fix as addExplicit()'s matching comment
            // (RM_FRO_011/FRO_023) -- just never applied here until testing the fixLayers() work
            // turned up the gap (at the time, growth's layer = previous.layer() + 1 could
            // legitimately collide with an off-path border already holding that value; that specific
            // trigger no longer applies now that layer collisions aren't rejected at all -- see
            // BordersCrudFacet.validateProposal's doc -- but this stays as real defense for the
            // other rejection failureReason() still checks, chiefly radius bounds, which organic
            // growth's own GROWTH_FACTOR curve could in principle still exceed over enough growths).
            ctx.getSource().sendFailure(msg("Rejected: " + e.getMessage()));
            return 0;
        }

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
        var path = BorderAPI.borders(level)
                .map(b -> b.PATH)
                .orElseThrow();

        path.insert(path.size(), border); //probably crashes, but it's a TODO anyway

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

        var path = BorderAPI.borders(level)
                .map(b -> b.PATH)
                .orElseThrow();

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
        var path = BorderAPI.borders(level)
                .map(b -> b.PATH)
                .orElseThrow();
        path.moveUp(border);

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

        var path = BorderAPI.borders(level)
                .map(b -> b.PATH)
                .orElseThrow();
        path.moveDown(border);

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
        var path = BorderAPI.borders(level)
                .map(b -> b.PATH)
                .orElseThrow();

        // RM_FRO_015: fixLayers() is now the real reorder-to-match-path-order reconciliation --
        // reports the two real outcomes distinctly instead of the old unconditional "not
        // implemented yet" placeholder (see Border Path & Layer Reconciliation, "Command-layer
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
        ServerLevel level = ctx.getSource().getLevel();

        var opt = BorderAPI.borders(level);

        if (opt.isEmpty()) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("[Border][Debug] Borders bundle: NOT PRESENT"),
                    false
            );
            return 0;
        }

        var borders = opt.get().CRUD.all();

        ctx.getSource().sendSuccess(
                () -> Component.literal(
                        "[Border][Debug] Borders bundle present. Count = " + borders.size()
                ),
                false
        );

        return borders.size();
    }
    public static int debugCreate(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();

        var optBorders = BorderAPI
                .levelJig()
                .getOrCreate(
                        BorderAPI.scope(level),
                        FrontierKeys.BORDERS_BUNDLE
                )
                .get(FrontierKeys.BORDERS);

        if (optBorders.isEmpty()) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal(
                            "[Border][Debug] Borders bundle exists, but Borders facet is ABSENT"
                    ),
                    false
            );
            return 0;
        }

        var borders = optBorders.get();
        int count = borders.CRUD.all().size();

        ctx.getSource().sendSuccess(
                () -> Component.literal(
                        "[Border][Debug] Borders facet present. Count = " + count
                ),
                false
        );

        return count;

    }

}
