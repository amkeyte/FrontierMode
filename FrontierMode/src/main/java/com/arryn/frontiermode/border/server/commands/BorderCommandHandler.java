package com.arryn.frontiermode.border.server.commands;

import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BorderDisplay;
import com.arryn.frontiermode.border.common.fixture.Result;
import com.arryn.frontiermode.boss.BossAPI;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.ForgeEventFactory;

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

        // FRO_047: BorderAPI.addBorder now returns Result instead of throwing -- same rejection
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

        ServerPlayer sp = ctx.getSource().getPlayerOrException();

        Result result = BorderAPI.addBorder(
                sp.serverLevel(),
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
        // FRO_047: removeBorder now returns Result instead of boolean -- carries the real reason
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

        ServerLevel level = ctx.getSource().getLevel();

        BorderAPI.border(level, id).ifPresentOrElse(
                b -> {
                    // LEV is this border's position in the canonical path order, distinct from LAY
                    // (Border.layer()) -- see RM_FRO_015: the two are definitionally unrelated, so
                    // both are shown rather than conflating them the way the old "L:" label did.
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
            // same fix (RM_FRO_011 / FRO_023).
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

    public static int pathGrow(CommandContext<CommandSourceStack> ctx, BlockPos center)
            throws CommandSyntaxException {

        ServerLevel level = ctx.getSource().getLevel();

        // RM_FRO_015: removed an "if no borders exist, refuse to grow" guard that used to sit
        // here. It was backwards -- BordersPathFacet.grow() already branches internally on an
        // empty path and bootstraps for exactly that case, which is precisely how a level's very
        // first border is supposed to get created. Command and trigger behave the same way; an
        // absent tip isn't special for the explicit-center overload below either (see its own
        // doc).

        // FRO_080: BorderAPI.grow(Level) deprecated, no-implicit-default center required instead
        // -- see BorderCommands.path()'s new "center" argument and BorderAPI.grow(Level)'s own
        // doc comment for the one remaining caller that still legitimately needs it.
        Result result = BorderAPI.grow(level, center);
        if (!result.isSuccess()) {
            // RM_FRO_015: same rejection path/fix as addExplicit()'s matching comment
            // (RM_FRO_011/FRO_023) -- just never applied here until testing the fixLayers() work
            // turned up the gap.
            ctx.getSource().sendFailure(msg("Rejected: " + result.message()));
            return 0;
        }

        // FRO_082 (FRO_063's ruling, boss.md's "Boss-less path layers and attach" section):
        // pathGrow() is deliberately boss-less (FRO_048) -- the newly-grown border's id is
        // marked pendingAttach so BossModule's own reconciliation check reads this as a
        // sanctioned wait-state ("awaiting /boss attach"), not a real data bug, until an admin
        // runs /boss attach against it. Same "whoever calls a border-creating operation also
        // calls into the sibling module's own API right after" shape every other paired call
        // site in this codebase uses -- but note this is the reverse direction from every other
        // instance of that shape: those all live on Boss's own side calling into BorderAPI
        // (Boss depends on Border, never the reverse -- see BossModule's class doc and FRO_075).
        // This one call, explicitly named by both FRO_082's ticket text and boss.md's own ruling,
        // is the one place Border imports Boss (BossAPI) -- a deliberate, ticket-mandated
        // exception to that stated direction, not an oversight; flagged here and in FRO_082's own
        // ticket log for whoever revisits this boundary later. A missing BossAPI.CRUD(level)
        // here (Boss's own data not resolvable yet) is a silent no-op, same "standby, don't
        // crash" discipline every other BossAPI resolution failure in this codebase already
        // follows -- the grow itself already succeeded and is not rolled back for it.
        BossAPI.CRUD(level).ifPresent(crud -> crud.addPendingAttach(result.border().id()));

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
        var path = BorderAPI.PATH(level).orElseThrow();

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

        var path = BorderAPI.PATH(level).orElseThrow();

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
        var path = BorderAPI.PATH(level).orElseThrow();
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

        var path = BorderAPI.PATH(level).orElseThrow();
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
        var path = BorderAPI.PATH(level).orElseThrow();

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
    // RM_FRO_037 ("Brenda") test aid: unlike /summon (which loads a raw entity from NBT and
    // never touches Mob#finalizeSpawn at all), this routes each spawned rabbit through the real
    // Forge dispatch -- ForgeEventFactory.onFinalizeSpawn constructs and posts the actual
    // MobSpawnEvent.FinalizeSpawn, then calls finalizeSpawn itself if nothing cancels it. That's
    // the same event BorderModule.onMobSpawnFinalize (Sick Wildlife's density hook) and
    // BossModule.onMobSpawnFinalize are subscribed to, so this is a deterministic way to exercise
    // that hook on demand instead of waiting on natural spawn RNG. Deliberately not the
    // DefaultBossRules.materialize()/spawnDensityCompanion() pattern -- those call
    // Mob#finalizeSpawn directly precisely to AVOID re-entering the event bus; this command wants
    // the opposite.
    // ------------------------------------------------------------------
    private static final RandomSource DEBUG_RNG = RandomSource.create();

    public static int spawnTestAnimals(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = ctx.getSource().getLevel();

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Mob mob = EntityType.RABBIT.create(level);
            if (mob == null) {
                continue;
            }

            int dx = DEBUG_RNG.nextInt(29) - 14;
            int dz = DEBUG_RNG.nextInt(29) - 14;
            BlockPos ground = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    player.blockPosition().offset(dx, 0, dz));

            mob.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, DEBUG_RNG.nextFloat() * 360.0F, 0.0F);

            ForgeEventFactory.onFinalizeSpawn(
                    mob, level, level.getCurrentDifficultyAt(ground), MobSpawnType.EVENT, null, null);

            if (level.addFreshEntity(mob)) {
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
