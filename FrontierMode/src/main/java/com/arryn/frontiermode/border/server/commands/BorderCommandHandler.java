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

        var border = BorderAPI.addBorder(level, pos, radius, layer);
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

        Border border = BorderAPI.addBorder(
                sp.serverLevel(),
                sp.blockPosition(),
                radius,
                layer
        );

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

        BorderAPI.border(ctx.getSource().getLevel(), id).ifPresentOrElse(
                b -> ctx.getSource().sendSuccess(
                        () -> msg(BorderDisplay.shortInfo(b)),
                        false
                ),
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
        Border out = BorderAPI.transformBorder(
                ctx.getSource().getLevel(),
                id,
                center,
                radius
        );


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

        var borders = BorderAPI.borders(level)
                .map(b -> b.CRUD.all())
                .orElse(List.of());

        if (borders.isEmpty()) {
            throw new SimpleCommandExceptionType(
                    Component.literal("No borders exist to grow.")
            ).create();
        }

        BorderAPI.grow(level);

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
        path.fixLayers();
        ctx.getSource().sendSuccess(
                () -> msg("Reconciled border layers with path order."),
                false
        );
        return 1;
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
