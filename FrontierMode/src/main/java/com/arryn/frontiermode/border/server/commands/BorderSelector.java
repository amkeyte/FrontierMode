package com.arryn.frontiermode.border.server.commands;

import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.BorderAPI;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

public final class BorderSelector {

    private BorderSelector() {
    }

    private static final SimpleCommandExceptionType UNKNOWN_SELECTOR = new SimpleCommandExceptionType(Component.literal("Unknown border selector"));

    private static final SimpleCommandExceptionType INVALID_INDEX = new SimpleCommandExceptionType(Component.literal("Invalid border index"));

    private static final SimpleCommandExceptionType COORD_ERROR = new SimpleCommandExceptionType(Component.literal("Usage: @coord <x> <y> <z>"));

    // ------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------

    public static BorderSelectorResult parse(StringReader reader) throws CommandSyntaxException {
        reader.skipWhitespace();

        if (!reader.canRead()) {
            return BorderSelectorResult.relevant();
        }

        char c = reader.peek();

        if (Character.isDigit(c)) {
            int index = reader.readInt();
            return BorderSelectorResult.index(index);
        }

        if (c == '@') {
            reader.read();
            if (!reader.canRead()) {
                throw UNKNOWN_SELECTOR.createWithContext(reader);
            }

            String token = readToken(reader).toLowerCase();

            return switch (token) {
                case "containing" -> BorderSelectorResult.inside();
                case "coord" -> BorderSelectorResult.coord(parseBlockPos(reader));
                case "all" -> BorderSelectorResult.all();
                case "relevant" -> BorderSelectorResult.relevant();
                case "none" -> BorderSelectorResult.none();
                default -> throw UNKNOWN_SELECTOR.createWithContext(reader);
            };
        }

        return BorderSelectorResult.relevant();
    }

    private static String readToken(StringReader reader) {
        StringBuilder sb = new StringBuilder();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
            sb.append(reader.read());
        }
        return sb.toString();
    }

    private static BlockPos parseBlockPos(StringReader reader) throws CommandSyntaxException {
        reader.skipWhitespace();
        if (!reader.canRead()) throw COORD_ERROR.createWithContext(reader);
        int x = reader.readInt();

        reader.skipWhitespace();
        if (!reader.canRead()) throw COORD_ERROR.createWithContext(reader);
        int y = reader.readInt();

        reader.skipWhitespace();
        if (!reader.canRead()) throw COORD_ERROR.createWithContext(reader);
        int z = reader.readInt();

        return new BlockPos(x, y, z);
    }

    // ------------------------------------------------------------
    // Resolution
    // ------------------------------------------------------------

    public static List<Border> resolve(BorderSelectorResult sel, ServerPlayer player) throws CommandSyntaxException {

        ServerLevel level = player.serverLevel();
        List<Border> borders =
                BorderAPI.borders(level)
                        .map(b -> b.CRUD.all())
                        .orElseGet(List::of);

        return switch (sel.mode) {
            case NONE -> List.of();
            case ALL -> resolveAll(level);
            case INDEX -> resolveIndex(borders, sel.index);
            case INSIDE -> resolveInside(player);
            case COORD -> resolveCoord(level, sel.coord);
            case RELEVANT -> resolveRelevant(player);
        };
    }

    private static List<Border> resolveIndex(List<Border> borders, int index) throws CommandSyntaxException {

        if (index < 0 || index >= borders.size()) {
            throw INVALID_INDEX.create();
        }
        return List.of(borders.get(index));
    }

    private static List<Border> resolveAll(ServerLevel level) {
        return BorderAPI.borders(level)
                        .map(b -> b.CRUD.all())
                        .orElseGet(List::of);
    }

    private static List<Border> resolveInside(ServerPlayer player) {
        return BorderAPI.bordersContaining(player.serverLevel(), player.blockPosition()).stream().toList();
    }

    private static List<Border> resolveCoord(ServerLevel level, BlockPos coord) {
        return BorderAPI.bordersContaining(level, coord).stream().toList();
    }

    private static List<Border> resolveRelevant(ServerPlayer player) {
        // RM_FRO_006: BorderAPI.getRelevant(ServerPlayer) is real now -- backed by the
        // PlayerJig-scoped BorderPlayerStatusFixture BorderModule keeps current every tick.
        // Empty here is a real, honest result (no nearest border yet -- e.g. an empty dimension,
        // or the player hasn't ticked since logging in), same as every other selector mode's
        // empty case, not the "feature isn't built" placeholder this used to be.
        return BorderAPI.getRelevant(player).stream().toList();
    }

    public static Border resolveSingle(BorderSelectorResult sel, ServerPlayer player) throws CommandSyntaxException {

        List<Border> resolved = resolve(sel, player);

        if (resolved.isEmpty()) {
            throw new SimpleCommandExceptionType(Component.literal("Selector resolved to no borders.")).create();
        }

        if (resolved.size() > 1) {
            throw new SimpleCommandExceptionType(Component.literal("Selector resolved to multiple borders (" + resolved.size() + "). Exactly one is required.")).create();
        }

        return resolved.get(0);
    }

    public static Optional<Border> resolveOptional(BorderSelectorResult sel, ServerPlayer player) throws CommandSyntaxException {

        List<Border> resolved = resolve(sel, player);

        if (resolved.size() > 1) {
            throw new SimpleCommandExceptionType(Component.literal("Selector resolved to multiple borders (" + resolved.size() + "). At most one is allowed.")).create();
        }

        return resolved.stream().findFirst();
    }
}
