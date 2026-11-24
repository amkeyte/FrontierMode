package com.arryn.frontiermode.core;

import com.arryn.frontiermode.core.server.FrontierServerLine;
import com.arryn.frontiermode.core.server.MobSpawns;
import com.arryn.satchel.common.platform.PlatformBound;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.event.EventHooks;

import java.util.Optional;

/** {@code neoforge-1.21.1}'s {@link FrontierLine.Source} and {@link FrontierServerLine.Source}: each call as Minecraft 1.21.1 and NeoForge 21.1 have it. */
@PlatformBound(reason = "vanilla calls that differ by line")
public final class NeoForgeLineSource implements FrontierLine.Source, FrontierServerLine.Source {

    @Override
    public ChunkAccess fullChunkIfLoaded(Level level, int chunkX, int chunkZ) {
        return level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void finalizeSpawn(Mob mob, ServerLevel level, BlockPos pos, MobSpawns.Setup setup) {
        switch (setup) {
            case DISPATCHED -> EventHooks.finalizeMobSpawn(
                    mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            case VANILLA -> mob.finalizeSpawn(
                    level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        }
    }

    @Override
    public void raiseStepHeight(Entity entity, float height) {
        if (entity instanceof LivingEntity living) {
            AttributeInstance step = living.getAttribute(Attributes.STEP_HEIGHT);
            if (step != null && step.getBaseValue() < height) {
                step.setBaseValue(height);
            }
        }
    }

    @Override
    public Optional<LootTable> lootTable(ServerLevel level, ResourceLocation id) {
        LootTable table = level.getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
        return table == LootTable.EMPTY ? Optional.empty() : Optional.of(table);
    }

    @Override
    public LootParams.Builder withKiller(LootParams.Builder params, Entity killer) {
        return params
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, killer)
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer);
    }

    @Override
    public Optional<ConfiguredFeature<?, ?>> configuredFeature(ServerLevel level, ResourceLocation id) {
        return level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .getHolder(ResourceKey.create(Registries.CONFIGURED_FEATURE, id))
                .map(holder -> holder.value());
    }

    @Override
    public MerchantOffer merchantOffer(ItemStack cost, ItemStack result, int maxUses, int xp, float priceMultiplier) {
        return new MerchantOffer(
                new ItemCost(cost.getItem(), cost.getCount()), Optional.empty(), result, maxUses, xp, priceMultiplier);
    }

    @Override
    public BlockBehaviour.Properties copyProperties(Block block) {
        return BlockBehaviour.Properties.ofFullCopy(block);
    }

    @Override
    public void damageItem(ItemStack stack, int amount, Player player, InteractionHand hand) {
        stack.hurtAndBreak(amount, player, LivingEntity.getSlotForHand(hand));
    }

    @Override
    public ItemStack enchantedBook(Level level, ResourceLocation enchantment, int enchantLevel) {
        Holder<Enchantment> holder = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(ResourceKey.create(Registries.ENCHANTMENT, enchantment));
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder, enchantLevel));
    }
}
