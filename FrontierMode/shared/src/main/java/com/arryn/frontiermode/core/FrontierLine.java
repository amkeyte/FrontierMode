package com.arryn.frontiermode.core;

import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.Objects;

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

        void raiseStepHeight(Entity entity, float height);

        LootParams.Builder withKiller(LootParams.Builder params, Entity killer);

        MerchantOffer merchantOffer(ItemStack cost, ItemStack result, int maxUses, int xp, float priceMultiplier);

        BlockBehaviour.Properties copyProperties(Block block);

        void damageItem(ItemStack stack, int amount, Player player, InteractionHand hand);

        ItemStack enchantedBook(Level level, ResourceLocation enchantment, int enchantLevel);
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

    /** Raises {@code entity}'s step height to at least {@code height}. It never lowers one. */
    public static void raiseStepHeight(Entity entity, float height) {
        required().raiseStepHeight(entity, height);
    }

    /** Adds {@code killer} as both the killer and the direct killer of the loot parameters. */
    public static LootParams.Builder withKiller(LootParams.Builder params, Entity killer) {
        return required().withKiller(params, killer);
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

    /** An enchanted book holding {@code enchantment} at {@code enchantLevel}. The id must name an enchantment. */
    public static ItemStack enchantedBook(Level level, ResourceLocation enchantment, int enchantLevel) {
        return required().enchantedBook(level, enchantment, enchantLevel);
    }

    private static Source required() {
        Source installed = source;
        if (installed == null) {
            throw new IllegalStateException("No line is installed: the mod entry installs one first");
        }
        return installed;
    }
}
