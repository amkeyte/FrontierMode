package com.arryn.frontiermode.boss.server.commands;

import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.boss.common.DefeatOutcome;
import com.arryn.frontiermode.boss.common.fixture.MaterializeOutcome;
import com.arryn.frontiermode.boss.common.fixture.BossDisplay;
import com.arryn.frontiermode.boss.common.BossKind;
import com.arryn.frontiermode.boss.common.BossKinds;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.Result;
import com.arryn.frontiermode.border.server.commands.BorderSelector;
import com.arryn.frontiermode.border.server.commands.BorderSelectorResult;
import com.arryn.frontiermode.keeper.KeeperKind;
import com.arryn.frontiermode.core.InfoPrinter;
import com.arryn.frontiermode.core.InfoRequest;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.util.Ids;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handler logic for {@code /boss}'s six FRO_057 leaves -- Border's registration/handler split,
 * applied to Boss. Uses Optional/boolean feedback throughout, matching {@code BossFixture}/
 * {@code BossAPI}'s own current idiom (this build does not introduce Border's {@code Result}
 * type onto Boss wholesale -- {@link #transformDefeat} is the one place {@code Result} appears,
 * because it wraps {@link BossAPI#forceDefeat}, which itself wraps a real {@code BorderAPI.grow}
 * call and so returns what that call already produces).
 */
public final class BossCommandHandler {

    private BossCommandHandler() {
    }

    private static Component msg(String m) {
        return Component.literal("[Boss] " + m);
    }

    // /boss add and /boss attach are the kind's verbs (BossKind.handPlace, BossKind.attachToBorder). Until the node F
    // rules split they name the Keeper's kind.
    private static BossKind keeperKind() {
        return BossKinds.get(KeeperKind.KEY).orElseThrow();
    }

    // A kind that does not offer a verb answers that it does not apply.
    private static Component doesNotApply(BossKind kind, String verb) {
        return msg("/boss " + verb + " does not apply to the " + kind.key() + " kind.");
    }

    // ------------------------------------------------------------
    // INFO
    // ------------------------------------------------------------

    /**
     * {@code /boss info}: for each selected boss, the base header, then what the modules add about it (boss.md §
     * Commands and the Info report). A boss's subject is its body if it is loaded, otherwise its base record; one
     * {@link InfoRequest} carries them all, and the shared printer prints each subject's lines together. The header is
     * added the way the Inspect Stick adds its own, ahead of what the consumers add.
     */
    public static int info(CommandContext<CommandSourceStack> ctx, List<BossRecord> bosses) {
        ServerLevel level = ctx.getSource().getLevel();

        Optional<BossFixture> fixtureOpt = BossAPI.bosses(level);
        if (fixtureOpt.isEmpty()) {
            ctx.getSource().sendFailure(msg("Boss data not available for this level yet."));
            return 0;
        }
        BossFixture fixture = fixtureOpt.get();

        List<Object> subjects = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        for (BossRecord record : bosses) {
            Optional<Entity> body = record.materialized()
                    ? Satchel.require().egress().getEntity(level, record.bossEntityId())
                    : Optional.empty();
            subjects.add(body.isPresent() ? body.get() : record);
            // Surfaces the bare-position selector's own index (BossFixture.all()'s list order) -- otherwise
            // there's no way to target a specific record with /boss mob spawn <n> without counting list
            // position by hand.
            headers.add("[Boss] " + BossDisplay.header(record, fixture.all().indexOf(record)));
        }

        InfoRequest request = new InfoRequest(level, subjects);
        for (int i = 0; i < subjects.size(); i++) {
            String header = headers.get(i);
            request.add(subjects.get(i), "boss", () -> header);
        }
        InfoPrinter.report(request, line -> ctx.getSource().sendSuccess(() -> Component.literal(line), false));
        return bosses.size();
    }

    // ------------------------------------------------------------
    // ADD (no selector -- unchanged, same shape as Border's own add())
    // ------------------------------------------------------------

    public static int addExplicit(
            CommandContext<CommandSourceStack> ctx,
            BlockPos pos,
            int layer
    ) {
        ServerLevel level = ctx.getSource().getLevel();

        if (BossAPI.bosses(level).isEmpty()) {
            ctx.getSource().sendFailure(msg("Boss data not available for this level yet."));
            return 0;
        }
        BossKind kind = keeperKind();
        if (!kind.offersHandPlace()) {
            ctx.getSource().sendFailure(doesNotApply(kind, "add"));
            return 0;
        }

        // FRO_058: the kind answers empty on a rejected sub-1 layer -- Brigadier's own
        // IntegerArgumentType.integer(1) on "layer" already keeps this unreachable from this command, but the
        // kind-level guard applies to every caller. Border Pregeneration: the record starts unpositioned, and
        // this command's whole point is an admin choosing the position directly, so the kind commits `pos`
        // immediately rather than waiting on BorderAPI.isPregenReady() -- the same "admin-forced position,
        // unvalidated terrain is expected" precedent debugGoto's own comment already uses.
        Optional<BossRecord> recordOpt = kind.handPlace(level, pos, layer);
        if (recordOpt.isEmpty()) {
            ctx.getSource().sendFailure(msg("Rejected: no boss record was created for layer " + layer
                    + " (see server log)."));
            return 0;
        }

        BossRecord record = recordOpt.get();
        ctx.getSource().sendSuccess(() -> msg("Created boss record " + record.bossId()), false);
        return 1;
    }

    public static int addHere(
            CommandContext<CommandSourceStack> ctx,
            int layer
    ) throws CommandSyntaxException {

        ServerPlayer sp = ctx.getSource().getPlayerOrException();

        if (BossAPI.bosses(sp.serverLevel()).isEmpty()) {
            sp.sendSystemMessage(msg("Boss data not available for this level yet."));
            return 0;
        }
        BossKind kind = keeperKind();
        if (!kind.offersHandPlace()) {
            sp.sendSystemMessage(doesNotApply(kind, "add"));
            return 0;
        }

        // Same hand placement as addExplicit() -- see its comment.
        Optional<BossRecord> recordOpt = kind.handPlace(sp.serverLevel(), sp.blockPosition(), layer);
        if (recordOpt.isEmpty()) {
            sp.sendSystemMessage(msg("Rejected: no boss record was created for layer " + layer
                    + " (see server log)."));
            return 0;
        }

        BossRecord record = recordOpt.get();
        // FRO_087 fix: see addExplicit()'s matching comment above.
        sp.sendSystemMessage(msg("Created boss record " + record.bossId() + " at your location"));
        return 1;
    }

    // ------------------------------------------------------------
    // DELETE
    // ------------------------------------------------------------

    public static int delete(CommandContext<CommandSourceStack> ctx, UUID id) {
        ServerLevel level = ctx.getSource().getLevel();

        // Routed through BossAPI.removeBoss() rather than calling BossFixture.remove(id) directly --
        // removeBoss() wraps the same removal, whose kind hook adds the pendingAttach step boss.md's
        // "Aliveness and removal" section describes. BossAPI.bosses(level)'s own "not available yet"
        // check lives inside removeBoss() itself.
        boolean removed = BossAPI.removeBoss(level, id);
        if (!removed) {
            ctx.getSource().sendFailure(msg("No boss record to remove: " + id
                    + " (or boss data not available for this level yet)."));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> msg("Removed boss record " + id), false);
        return 1;
    }

    // ------------------------------------------------------------
    // ATTACH
    // ------------------------------------------------------------

    /**
     * Pairs a boss onto an existing boss-less path border -- FRO_082 (FRO_063's ruling).
     * {@code add} with a border to bind to instead of a raw position: the kind's {@code attachToBorder}
     * creates the Keeper, which sets the new record's {@code borderId} to the selected border, then clears
     * that border from {@code pendingAttach}. See
     * wiki/frontiermode/architecture/keeper.md#boss-less-path-layers-and-attach.
     *
     * <p>Nests {@code BorderSelectorArgumentType} the same way {@code BorderCommandHandler
     * .pathInsert}/{@code .pathRemove} already do -- the argument is Border's own selector
     * grammar directly, resolved to exactly one border via {@code BorderSelector.resolveSingle}
     * (throws its own {@code CommandSyntaxException} on zero or more than one match), not a mode
     * nested inside {@code BossSelectorResult} -- {@code @border} as a {@code BossSelector} mode
     * is still documented proposal, not built (see boss-commands.md's "Selector scheme"; this
     * command doesn't need it, since it always creates a fresh record rather than resolving an
     * existing boss).
     */
    public static int attach(CommandContext<CommandSourceStack> ctx, BorderSelectorResult selector)
            throws CommandSyntaxException {

        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        ServerLevel level = ctx.getSource().getLevel();

        Border border = BorderSelector.resolveSingle(selector, sp);

        if (BossAPI.bosses(level).isEmpty()) {
            ctx.getSource().sendFailure(msg("Boss data not available for this level yet."));
            return 0;
        }
        BossKind kind = keeperKind();
        if (!kind.offersAttach()) {
            ctx.getSource().sendFailure(doesNotApply(kind, "attach"));
            return 0;
        }

        // Clearing pendingAttach is a no-op, not an error, if border.id() wasn't actually pending -- attach
        // doesn't require the target to already be in it (boss.md's own spec places no such restriction on it).
        // The Tell and Sentinel curves are the downstream modules' answer to the Keeper's KeeperCreated event.
        Optional<BossRecord> recordOpt = kind.attachToBorder(level, border);
        if (recordOpt.isEmpty()) {
            ctx.getSource().sendFailure(msg("Rejected: could not create a boss record for border "
                    + border.id() + " (a boss needs layer 1 or more -- see server log)."));
            return 0;
        }
        BossRecord record = recordOpt.get();

        ctx.getSource().sendSuccess(
                () -> msg("Attached boss " + record.bossId() + " to border " + border.id() + "."),
                false
        );
        return 1;
    }

    // ------------------------------------------------------------
    // MOB SPAWN
    // ------------------------------------------------------------

    public static int mobSpawn(CommandContext<CommandSourceStack> ctx, UUID id) {
        ServerLevel level = ctx.getSource().getLevel();

        MaterializeOutcome outcome = BossAPI.forceMaterialize(level, id);
        return switch (outcome) {
            case SPAWNED -> {
                ctx.getSource().sendSuccess(() -> msg("Force-spawned boss " + id), false);
                yield 1;
            }
            case SPAWNED_REPLACING -> {
                ctx.getSource().sendSuccess(() -> msg("Boss " + id + " was recorded as spawned but no entity"
                        + " was present -- spawned a fresh one at its spot."), false);
                yield 1;
            }
            case TELEPORTED_HOME -> {
                ctx.getSource().sendSuccess(() -> msg("Boss " + id + " was already alive -- teleported it back"
                        + " to its spot."), false);
                yield 1;
            }
            case NOT_POSITIONED -> {
                ctx.getSource().sendFailure(msg("Boss " + id + " has no spot yet: its home Border is still"
                        + " pregenerating. Try again in a moment."));
                yield 0;
            }
            case NO_RECORD -> {
                ctx.getSource().sendFailure(msg("No boss record for id " + id
                        + " (or boss data not available for this level yet)."));
                yield 0;
            }
            case DECLINED -> {
                ctx.getSource().sendFailure(msg("Could not spawn boss " + id
                        + " -- the entity could not be created or added at its stored position."
                        + " See the server log."));
                yield 0;
            }
        };
    }

    // ------------------------------------------------------------
    // TRANSFORM DEFEAT
    // ------------------------------------------------------------

    /**
     * Force-defeats a boss without combat -- the item that actually motivated Joyce. Runs the same defeat a real
     * death does, via {@link BossAPI#forceDefeat}, which marks the record defeated and calls the kind's
     * {@code onDefeat} -- so this reproduces the full "I just killed the boss!" effect (for a Keeper, the border
     * grows and the next boss is queued) on demand. The boss is named by short id and kind; the kind's message
     * says the rest (the Keeper says its level).
     */
    public static int transformDefeat(CommandContext<CommandSourceStack> ctx, UUID id) {
        ServerLevel level = ctx.getSource().getLevel();

        // Named before the defeat, so every line below says which boss it is about.
        String fallen = Ids.shortId(id) + BossAPI.bosses(level).flatMap(fixture -> fixture.get(id))
                .map(record -> " (" + record.kind() + ")").orElse("");

        DefeatOutcome outcome = BossAPI.forceDefeat(level, id);

        // FRO_083: an outcome that went through includes a defeat whose cascade was correctly withheld (an
        // off-path Keeper, or another still alive on the border) -- the kind's message says so, and it is a
        // success, not a command failure.
        if (!outcome.wentThrough()) {
            ctx.getSource().sendFailure(
                    msg("Failed to force-defeat boss " + fallen + ": " + outcome.message())
            );
            return 0;
        }

        ctx.getSource().sendSuccess(
                () -> msg("Boss " + fallen + ": " + outcome.message()),
                false
        );
        return 1;
    }

    // ------------------------------------------------------------
    // DEBUG GOTO
    // ------------------------------------------------------------

    public static int debugGoto(CommandContext<CommandSourceStack> ctx, UUID id)
            throws CommandSyntaxException {

        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        ServerLevel level = ctx.getSource().getLevel();

        Optional<BossFixture> fixtureOpt = BossAPI.bosses(level);
        if (fixtureOpt.isEmpty()) {
            ctx.getSource().sendFailure(msg("Boss data not available for this level yet."));
            return 0;
        }

        Optional<BossRecord> recordOpt = fixtureOpt.get().get(id);
        if (recordOpt.isEmpty()) {
            ctx.getSource().sendFailure(msg("Boss not found: " + id));
            return 0;
        }
        BossRecord record = recordOpt.get();

        // Border Pregeneration: position is nullable until BOSS_JIG's own tick finalizes it (see
        // BossRecord's own doc) -- supersedes this method's old "copy-once placeholder Y" wording,
        // since there's no placeholder position to land in anymore, just none yet.
        if (!record.positioned()) {
            ctx.getSource().sendFailure(msg("Boss " + id
                    + " has no position yet -- its home border is still pregenerating."));
            return 0;
        }

        // Spawned or not -- the stored position, deliberately (FRO_057's own wording). Landing
        // the player inside terrain for a not-yet-materialized-but-positioned boss is expected
        // debug-tool behavior per the wiki page's "escape hatch" framing, not a bug -- position is
        // already flatness/hazard-scored by this point regardless, so that's now rare in practice.
        BlockPos pos = record.position();
        sp.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        ctx.getSource().sendSuccess(
                () -> msg("Teleported to boss " + id + "'s stored position."),
                false
        );
        return 1;
    }

    // ------------------------------------------------------------
    // DEBUG DISTANCE -- not in FRO_057's original six, added mid-playtest to make finding an
    // unmaterialized (and possibly unloaded) boss practical without teleporting blind. Pairs
    // with debugGoto the same way "how far" pairs with "go there"; not yet documented on
    // wiki/frontiermode/architecture/boss-commands.md -- flagged for the Architect to reconcile.
    // ------------------------------------------------------------

    public static int debugDistance(CommandContext<CommandSourceStack> ctx, UUID id)
            throws CommandSyntaxException {

        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        ServerLevel level = ctx.getSource().getLevel();

        Optional<BossFixture> fixtureOpt = BossAPI.bosses(level);
        if (fixtureOpt.isEmpty()) {
            ctx.getSource().sendFailure(msg("Boss data not available for this level yet."));
            return 0;
        }

        Optional<BossRecord> recordOpt = fixtureOpt.get().get(id);
        if (recordOpt.isEmpty()) {
            ctx.getSource().sendFailure(msg("Boss not found: " + id));
            return 0;
        }
        BossRecord record = recordOpt.get();

        // Border Pregeneration: same nullable-position guard debugGoto uses -- see that method's
        // own comment.
        if (!record.positioned()) {
            ctx.getSource().sendFailure(msg("Boss " + id
                    + " has no position yet -- its home border is still pregenerating."));
            return 0;
        }

        // Same stored position debugGoto teleports to -- spawned or not, already validated
        // terrain by this point (see debugGoto's own comment).
        BlockPos pos = record.position();
        double dx = (pos.getX() + 0.5) - sp.getX();
        double dy = pos.getY() - sp.getY();
        double dz = (pos.getZ() + 0.5) - sp.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        ctx.getSource().sendSuccess(
                () -> msg(String.format(
                        "Boss %s is %.1f blocks away (pos: %d, %d, %d).",
                        id, dist, pos.getX(), pos.getY(), pos.getZ()
                )),
                false
        );
        return 1;
    }
}
