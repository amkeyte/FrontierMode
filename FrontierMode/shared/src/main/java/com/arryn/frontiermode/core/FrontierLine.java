package com.arryn.frontiermode.core;

import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Objects;
import java.util.Optional;

/**
 * The calls into Minecraft that shared logic makes and whose form differs by line (Two Lines § The vanilla calls that
 * differ). Each is one static method; the line's {@link Source} makes the call the way that line has it. Each line's
 * mod entry installs its source once, before anything calls here.
 *
 * <p>
 * Asking before a source is installed throws. That is a programming error, and it crashes in every build.
 */
@PlatformBound(reason = "vanilla calls that differ by line")
public final class FrontierLine {

    /** How a line makes each call. Implemented once per line. */
    public interface Source {

        ChunkAccess fullChunkIfLoaded(Level level, int chunkX, int chunkZ);

        void finalizeSpawn(Mob mob, ServerLevel level, BlockPos pos, MobSpawns.Setup setup);

        void raiseStepHeight(Entity entity, float height);

        Optional<LootTable> lootTable(ServerLevel level, ResourceLocation id);

        LootParams.Builder withKiller(LootParams.Builder params, Entity killer);

        Optional<ConfiguredFeature<?, ?>> configuredFeature(ServerLevel level, ResourceLocation id);

        MerchantOffer merchantOffer(ItemStack cost, ItemStack result, int maxUses, int xp, float priceMultiplier);

        BlockBehaviour.Properties copyProperties(Block block);

        void damageItem(ItemStack stack, int amount, Player player, InteractionHand hand);
    }

    private static volatile Source source;

    private FrontierLine() {
    }

    /** Installs the line's calls. Once: a second call throws. */
    public static synchronized void install(Source installed) {
        Objects.requireNonNull(installed, "installed");
        if (source != null) {
            throw new IllegalStateException("The line is already installed (" + source.getClass().getName() + ")");
        }
        source = installed;
    }

    /**
     * The chunk at {@code chunkX, chunkZ} if it is in memory at full status, else null. It never loads or generates
     * one, and it does not consult the disk.
     */
    public static ChunkAccess fullChunkIfLoaded(Level level, int chunkX, int chunkZ) {
        return required().fullChunkIfLoaded(level, chunkX, chunkZ);
    }

    /**
     * Runs {@code setup} on a mob that has been created and placed: the loader's finalize-spawn event and then the
     * vanilla setup for {@link MobSpawns.Setup#DISPATCHED}, the vanilla setup alone for {@code VANILLA}.
     */
    public static void finalizeSpawn(Mob mob, ServerLevel level, BlockPos pos, MobSpawns.Setup setup) {
        required().finalizeSpawn(mob, level, pos, setup);
    }

    /** Raises {@code entity}'s step height to at least {@code height}. It never lowers one. */
    public static void raiseStepHeight(Entity entity, float height) {
        required().raiseStepHeight(entity, height);
    }

    /** The loot table {@code id} names, or empty if the server has none. */
    public static Optional<LootTable> lootTable(ServerLevel level, ResourceLocation id) {
        return required().lootTable(level, id);
    }

    /** Adds {@code killer} as both the killer and the direct killer of the loot parameters. */
    public static LootParams.Builder withKiller(LootParams.Builder params, Entity killer) {
        return required().withKiller(params, killer);
    }

    /** The configured feature {@code id} names, or empty if none is registered. */
    public static Optional<ConfiguredFeature<?, ?>> configuredFeature(ServerLevel level, ResourceLocation id) {
        return required().configuredFeature(level, id);
    }

    /** A trade of {@code cost} for {@code result}, with no second cost. */
    public static MerchantOffer merchantOffer(ItemStack cost, ItemStack result, int maxUses, int xp,
                                              float priceMultiplier) {
        return required().merchantOffer(cost, result, maxUses, xp, priceMultiplier);
    }

    /** {@code block}'s properties, to build another block from. */
    public static BlockBehaviour.Properties copyProperties(Block block) {
        return required().copyProperties(block);
    }

    /** Wears {@code stack} by {@code amount} for {@code player} using it in {@code hand}, breaking it at zero. */
    public static void damageItem(ItemStack stack, int amount, Player player, InteractionHand hand) {
        required().damageItem(stack, amount, player, hand);
    }

    private static Source required() {
        Source installed = source;
        if (installed == null) {
            throw new IllegalStateException("No line is installed: the mod entry installs one first");
        }
        return installed;
    }
}
