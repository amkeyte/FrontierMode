package com.arryn.frontiermode.feral.server.commands;

import com.arryn.frontiermode.feral.FeralModule;
import com.arryn.frontiermode.spawn.MobSpawns;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.scores.Team;

/**
 * Backs {@code /feral debug spawn} -- same real-{@code FinalizeSpawn} shape as
 * {@code BorderCommandHandler.spawnTestAnimals}, a hostile instead of a passive so
 * {@code FeralModule.onMobSpawnFinalize}'s branch can be exercised on demand instead of waiting
 * on natural spawn RNG. Unlike {@code /summon} (which loads a raw entity from NBT and never
 * touches {@code Mob#finalizeSpawn} at all), this routes each spawned zombie through the real
 * Forge dispatch -- {@code ForgeEventFactory.onFinalizeSpawn} constructs and posts the actual
 * {@code MobSpawnEvent.FinalizeSpawn}, then calls {@code finalizeSpawn} itself if nothing cancels
 * it -- the same event {@code FeralModule.onMobSpawnFinalize} is subscribed to.
 */
@SuppressWarnings("SameReturnValue")
public final class FeralCommandHandler {

    private FeralCommandHandler() {
    }

    private static final RandomSource DEBUG_RNG = RandomSource.create();

    public static int spawnTestHostiles(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = ctx.getSource().getLevel();

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            int dx = DEBUG_RNG.nextInt(29) - 14;
            int dz = DEBUG_RNG.nextInt(29) - 14;
            BlockPos ground = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    player.blockPosition().offset(dx, 0, dz));

            if (MobSpawns.spawn(level, EntityType.ZOMBIE, ground, MobSpawns.Setup.DISPATCHED).isPresent()) {
                spawned++;
            }
        }

        int finalSpawned = spawned;
        ctx.getSource().sendSuccess(
                () -> Component.literal(
                        "[Feral][Debug] Spawned " + finalSpawned + "/" + count
                                + " test zombies via a real FinalizeSpawn event (unlike /summon)."
                ),
                false
        );

        return spawned;
    }
}
