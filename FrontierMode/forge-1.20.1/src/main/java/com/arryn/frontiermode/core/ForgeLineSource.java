package com.arryn.frontiermode.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.Optional;

/** {@code forge-1.20.1}'s {@link FrontierLine.Source}: each call as Minecraft 1.20.1 and Forge 47 have it. */
public final class ForgeLineSource implements FrontierLine.Source {

    @Override
    public ChunkAccess fullChunkIfLoaded(Level level, int chunkX, int chunkZ) {
        return level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void finalizeSpawn(Mob mob, ServerLevel level, BlockPos pos, MobSpawns.Setup setup) {
        switch (setup) {
            case DISPATCHED -> ForgeEventFactory.onFinalizeSpawn(
                    mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            case VANILLA -> mob.finalizeSpawn(
                    level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        }
    }

    @Override
    public void raiseStepHeight(Entity entity, float height) {
        entity.setMaxUpStep(Math.max(entity.maxUpStep(), height));
    }

    @Override
    public Optional<LootTable> lootTable(ServerLevel level, ResourceLocation id) {
        LootTable table = level.getServer().getLootData().getLootTable(id);
        return table == LootTable.EMPTY ? Optional.empty() : Optional.of(table);
    }

    @Override
    public LootParams.Builder withKiller(LootParams.Builder params, Entity killer) {
        return params
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, killer)
                .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, killer);
    }

    @Override
    public Optional<ConfiguredFeature<?, ?>> configuredFeature(ServerLevel level, ResourceLocation id) {
        return level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .getHolder(ResourceKey.create(Registries.CONFIGURED_FEATURE, id))
                .map(holder -> holder.value());
    }

    @Override
    public MerchantOffer merchantOffer(ItemStack cost, ItemStack result, int maxUses, int xp, float priceMultiplier) {
        return new MerchantOffer(cost, ItemStack.EMPTY, result, maxUses, xp, priceMultiplier);
    }

    @Override
    public BlockBehaviour.Properties copyProperties(Block block) {
        return BlockBehaviour.Properties.copy(block);
    }

    @Override
    public void damageItem(ItemStack stack, int amount, Player player, InteractionHand hand) {
        stack.hurtAndBreak(amount, player, p -> p.broadcastBreakEvent(hand));
    }
}
