package com.arryn.frontiermode.boss.server.commands;

import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;

/**
 * Parses and resolves {@code /boss}'s selector chain -- the four modes FRO_057 scopes this build
 * to (bare position / {@code @nearest} / {@code @all} / {@code @none}). See
 * wiki/frontiermode/architecture/boss-commands.md's "Selector scheme" for the full design space
 * this is deliberately a subset of -- no {@code @id}/{@code @name}/{@code @border} this pass, no
 * {@code borderId} on {@code BossRecord} needed for it. Mirrors {@code BorderSelector}'s parse/
 * resolve split exactly.
 */
public final class BossSelector {

    private BossSelector() {
    }

    private static final SimpleCommandExceptionType UNKNOWN_SELECTOR =
            new SimpleCommandExceptionType(Component.literal("Unknown boss selector"));

    private static final SimpleCommandExceptionType INVALID_INDEX =
            new SimpleCommandExceptionType(Component.literal("Invalid boss index"));

    // ------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------

    public static BossSelectorResult parse(StringReader reader) throws CommandSyntaxException {
        reader.skipWhitespace();

        // No selector text at all -- matches Border's own explicit no-arg default (@all), not
        // Border's @relevant (Boss has no per-player relevance concept to mirror that with).
        if (!reader.canRead()) {
            return BossSelectorResult.all();
        }

        char c = reader.peek();

        if (Character.isDigit(c)) {
            int index = reader.readInt();
            return BossSelectorResult.position(index);
        }

        if (c == '@') {
            reader.read();
            if (!reader.canRead()) {
                throw UNKNOWN_SELECTOR.createWithContext(reader);
            }

            String token = readToken(reader).toLowerCase();

            return switch (token) {
                case "nearest" -> BossSelectorResult.nearest();
                case "all" -> BossSelectorResult.all();
                case "none" -> BossSelectorResult.none();
                default -> throw UNKNOWN_SELECTOR.createWithContext(reader);
            };
        }

        return BossSelectorResult.all();
    }

    private static String readToken(StringReader reader) {
        StringBuilder sb = new StringBuilder();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
            sb.append(reader.read());
        }
        return sb.toString();
    }

    // ------------------------------------------------------------
    // Resolution
    // ------------------------------------------------------------

    public static List<BossRecord> resolve(BossSelectorResult sel, ServerPlayer player)
            throws CommandSyntaxException {

        List<BossRecord> all = BossAPI.bosses(player.serverLevel())
                .map(BossFixture::all)
                .orElseGet(List::of);

        return switch (sel.mode) {
            case NONE -> List.of();
            case ALL -> all;
            case POSITION -> resolvePosition(all, sel.index);
            case NEAREST -> resolveNearest(all, player);
        };
    }

    private static List<BossRecord> resolvePosition(List<BossRecord> all, int index)
            throws CommandSyntaxException {
        if (index < 0 || index >= all.size()) {
            throw INVALID_INDEX.create();
        }
        return List.of(all.get(index));
    }

    /**
     * FRO_132 (project owner ask): nearest *active* boss to the command source, not raw nearest
     * -- distance is measured against each record's own stored {@code position()}, not a
     * live-entity search. Unlike {@code MobTrackCommands}' generic nearest-mob convention, we
     * already have the data set (every boss record); no reason to re-derive it from a world scan.
     *
     * <p>{@code BossRecord} has no dedicated "active" flag -- checked against the real data model
     * (this class's own doc, {@code BossRecord}'s doc), the two fields that actually capture it
     * are {@link BossRecord#materialized()} (a real entity has been placed for this record) and
     * {@link BossRecord#alive()} (not yet defeated -- {@code false} once
     * {@code BossModule.onLivingDeath}/{@code BossAPI.forceDefeat} runs, and the record stays in
     * the fixture rather than being removed). "Active" is the conjunction of both: previously
     * this filtered only {@code materialized()}, so a defeated-but-still-recorded boss could still
     * win nearest -- the project owner's "usually the path tip" framing describes the common case
     * this produces (the frontier path's own tip is ordinarily the one boss still alive), not a
     * literal path lookup this selector needs to perform itself.
     */
    private static List<BossRecord> resolveNearest(List<BossRecord> all, ServerPlayer player) {
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        return all.stream()
                .filter(BossRecord::materialized)
                .filter(BossRecord::alive)
                .min(Comparator.comparingDouble(r -> {
                    var p = r.position();
                    double dx = p.getX() - px;
                    double dy = p.getY() - py;
                    double dz = p.getZ() - pz;
                    return dx * dx + dy * dy + dz * dz;
                }))
                .map(List::of)
                .orElseGet(List::of);
    }
}
