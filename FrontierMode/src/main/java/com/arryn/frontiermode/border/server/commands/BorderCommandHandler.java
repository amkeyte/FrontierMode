package com.arryn.frontiermode.border.server.commands;

import com.arryn.frontiermode.border.server.player.SwitchSet;
import com.arryn.frontiermode.core.MobSpawns;
import com.arryn.frontiermode.border.BorderAPI;
import com.arryn.frontiermode.border.server.player.SicknessSwitch;
import com.arryn.frontiermode.border.common.fixture.Border;
import com.arryn.frontiermode.border.common.fixture.BorderDisplay;
import com.arryn.frontiermode.border.common.fixture.Result;
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
        if (SicknessSwitch.set(ctx.getSource().getServer(), value) == SwitchSet.NOT_AVAILABLE) {
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
