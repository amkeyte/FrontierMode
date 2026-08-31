package com.arryn.frontiermode.boss.server.commands;

import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.frontiermode.boss.BossModule;
import com.arryn.frontiermode.boss.common.fixture.BossDisplay;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.frontiermode.border.common.fixture.Result;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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

    // ------------------------------------------------------------
    // INFO
    // ------------------------------------------------------------

    public static int info(CommandContext<CommandSourceStack> ctx, UUID id) {
        ServerLevel level = ctx.getSource().getLevel();

        Optional<BossFixture> fixtureOpt = BossAPI.bosses(level);
        if (fixtureOpt.isEmpty()) {
            ctx.getSource().sendFailure(msg("Boss data not available for this level yet."));
            return 0;
        }

        BossFixture fixture = fixtureOpt.get();
        Optional<BossRecord> recordOpt = fixture.get(id);
        recordOpt.ifPresentOrElse(
                r -> {
                    // Surfaces the bare-position selector's own index (BossFixture.all()'s list
                    // order) -- otherwise there's no way to target a specific record with
                    // /boss mob spawn <n> without counting list position by hand.
                    int idx = fixture.all().indexOf(r);
                    ctx.getSource().sendSuccess(() -> msg(BossDisplay.fullInfo(r, idx)), false);
                },
                () -> ctx.getSource().sendFailure(msg("Boss not found: " + id))
        );
        return 1;
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

        Optional<BossFixture> fixtureOpt = BossAPI.bosses(level);
        if (fixtureOpt.isEmpty()) {
            ctx.getSource().sendFailure(msg("Boss data not available for this level yet."));
            return 0;
        }
        BossFixture fixture = fixtureOpt.get();

        // FRO_058: create() now returns Optional<BossRecord> (empty on a rejected negative
        // layer) -- Brigadier's own IntegerArgumentType.integer(0) on "layer" already keeps this
        // unreachable from this command, but the fixture-level guard applies to every caller.
        // Border Pregeneration: create(layer) always starts the record unpositioned now -- this
        // command's whole point is an admin choosing the position directly, so it commits `pos`
        // immediately via finalizePosition() rather than waiting on BorderAPI.isPregenReady() --
        // same "admin-forced position, unvalidated terrain is expected" precedent debugGoto's own
        // comment already uses.
        Optional<BossRecord> recordOpt = fixture.create(layer);
        if (recordOpt.isEmpty()) {
            ctx.getSource().sendFailure(msg("Rejected: negative layer " + layer + "."));
            return 0;
        }

        BossRecord record = recordOpt.get();
        fixture.finalizePosition(record.bossId(), pos);
        ctx.getSource().sendSuccess(() -> msg("Created boss record " + record.bossId()), false);
        return 1;
    }

    public static int addHere(
            CommandContext<CommandSourceStack> ctx,
            int layer
    ) throws CommandSyntaxException {

        ServerPlayer sp = ctx.getSource().getPlayerOrException();

        Optional<BossFixture> fixtureOpt = BossAPI.bosses(sp.serverLevel());
        if (fixtureOpt.isEmpty()) {
            sp.sendSystemMessage(msg("Boss data not available for this level yet."));
            return 0;
        }
        BossFixture fixture = fixtureOpt.get();

        // FRO_058: same Optional<BossRecord> contract as addExplicit() above.
        // Border Pregeneration: same immediate finalizePosition() commit addExplicit() uses --
        // see that method's own comment.
        BlockPos pos = sp.blockPosition();
        Optional<BossRecord> recordOpt = fixture.create(layer);
        if (recordOpt.isEmpty()) {
            sp.sendSystemMessage(msg("Rejected: negative layer " + layer + "."));
            return 0;
        }

        BossRecord record = recordOpt.get();
        fixture.finalizePosition(record.bossId(), pos);
        sp.sendSystemMessage(msg("Created boss record " + record.bossId() + " at your location"));
        return 1;
    }

    // ------------------------------------------------------------
    // DELETE
    // ------------------------------------------------------------

    public static int delete(CommandContext<CommandSourceStack> ctx, UUID id) {
        ServerLevel level = ctx.getSource().getLevel();

        Optional<BossFixture> fixtureOpt = BossAPI.bosses(level);
        if (fixtureOpt.isEmpty()) {
            ctx.getSource().sendFailure(msg("Boss data not available for this level yet."));
            return 0;
        }

        boolean removed = fixtureOpt.get().remove(id);
        if (!removed) {
            ctx.getSource().sendFailure(msg("No boss record to remove: " + id));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> msg("Removed boss record " + id), false);
        return 1;
    }

    // ------------------------------------------------------------
    // MOB SPAWN
    // ------------------------------------------------------------

    public static int mobSpawn(CommandContext<CommandSourceStack> ctx, UUID id) {
        ServerLevel level = ctx.getSource().getLevel();

        BossModule.MaterializeOutcome outcome = BossModule.forceMaterialize(level, id);
        return switch (outcome) {
            case SPAWNED -> {
                ctx.getSource().sendSuccess(() -> msg("Force-spawned boss " + id), false);
                yield 1;
            }
            case ALREADY_MATERIALIZED -> {
                ctx.getSource().sendSuccess(
                        () -> msg("Boss " + id + " already has a spawned entity -- skipped."),
                        false
                );
                yield 0;
            }
            case NO_RECORD -> {
                ctx.getSource().sendFailure(msg("No boss record for id " + id
                        + " (or boss data not available for this level yet)."));
                yield 0;
            }
            case DECLINED -> {
                ctx.getSource().sendFailure(msg("Could not spawn boss " + id
                        + " -- no valid ground at its stored position (e.g. an all-liquid"
                        + " column). See server log."));
                yield 0;
            }
        };
    }

    // ------------------------------------------------------------
    // TRANSFORM DEFEAT
    // ------------------------------------------------------------

    /**
     * Force-defeats a boss without combat -- the item that actually motivated Joyce. Runs the
     * same cascade a real death does ({@code markDefeated} -> {@code BorderAPI.grow} ->
     * {@code BossAPI.createBoss}), via {@link BossAPI#forceDefeat}, rather than a bare flag flip
     * -- so this reproduces the full "I just killed the boss!" effect (border grows, next boss
     * queued) on demand.
     */
    public static int transformDefeat(CommandContext<CommandSourceStack> ctx, UUID id) {
        ServerLevel level = ctx.getSource().getLevel();

        BossAPI.DefeatOutcome outcome = BossAPI.forceDefeat(level, id);
        Result result = outcome.borderResult();
        if (!result.isSuccess()) {
            ctx.getSource().sendFailure(
                    msg("Failed to force-defeat boss " + id + ": " + result.message())
            );
            return 0;
        }

        // FRO_057 playtest bug: result.border().id() is the newly grown BORDER's id, not a
        // boss id -- reporting it as "next boss X" was actively misleading. outcome.nextBoss()
        // carries the real BossRecord (or is honestly empty if createBoss itself couldn't
        // resolve a fixture for it -- see BossAPI.forceDefeat's own warn log for that case).
        String nextBossText = outcome.nextBoss()
                .map(r -> r.bossId().toString())
                .orElse("(none -- see server log)");

        ctx.getSource().sendSuccess(
                () -> msg("Boss " + id + " defeated -- border " + result.border().id()
                        + " grown, next boss " + nextBossText + " queued."),
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
