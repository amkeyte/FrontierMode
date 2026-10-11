package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.border.server.rules.BorderRules;
import com.arryn.frontiermode.boss.common.KeeperLootTables;
import com.arryn.frontiermode.boss.common.fixture.BossRecord;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A defeated Keeper's reward (boss.md § Defeat loot): the vanilla-format tables under
 * {@code frontiermode:keeper/} that {@link KeeperLootTables} names, rolled with the credited player as
 * the killer and dropped as real items at the death position, so the reward is something a player walks
 * over and picks up. The body's own vanilla drops are untouched: they come from the death itself, with
 * Looting, and are where an Evoker Keeper's Totem of Undying comes from.
 *
 * <p>Past path position 20 the wave and milestone stacks grow by {@link KeeperLootTables#multiplier}. An
 * unstackable stack is never copied: each extra copy the multiplier would make is a roll of
 * {@code keeper/extra_piece} instead.
 */
final class KeeperLoot {

    private KeeperLoot() {
    }

    /**
     * Pays {@code record}'s defeat reward. Only reached for a counted, player-credited defeat that is the
     * last standing on its border; never from {@code BossAPI.forceDefeat} (an admin command
     * force-defeating bosses for loot is the riskless exploit this reward shouldn't have).
     */
    static void pay(ServerLevel level, Mob boss, BossRecord record, ServerPlayer credited, double lootStep) {
        int waveLength = BorderRules.ACTIVE.borderWaveLength();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, boss)
                .withParameter(LootContextParams.ORIGIN, boss.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, boss.damageSources().playerAttack(credited))
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, credited)
                .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, credited)
                .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, credited)
                .create(LootContextParamSets.ENTITY);
        // The record's own path position, not BossAPI.pathPositionOf: that falls back to the layer for an
        // off-path Keeper, which would hand it a multiplier the table choice (wave_1) never gave it.
        double multiplier = KeeperLootTables.multiplier(record.pathPosition(), waveLength, lootStep);

        List<ItemStack> payout = new ArrayList<>();
        int extraPieces = 0;
        for (String name : KeeperLootTables.names(record.pathPosition(), record.body(), waveLength)) {
            LootTable table = tableFor(level, name);
            if (table == LootTable.EMPTY) {
                continue;
            }
            boolean grows = multiplier > 1.0 && KeeperLootTables.growsPastTwenty(name);
            for (ItemStack rolled : table.getRandomItems(params)) {
                if (!grows) {
                    payout.add(rolled);
                    continue;
                }
                int count = KeeperLootTables.scaledCount(rolled.getCount(), multiplier);
                if (count <= 0) {
                    continue;
                }
                if (rolled.getMaxStackSize() == 1) {
                    payout.add(copyOf(rolled, 1));
                    extraPieces += count - 1;
                } else {
                    for (int size : KeeperLootTables.splitCounts(count, rolled.getMaxStackSize())) {
                        payout.add(copyOf(rolled, size));
                    }
                }
            }
        }
        payout.addAll(rollExtraPieces(level, params, payout, extraPieces));

        for (ItemStack stack : payout) {
            Containers.dropItemStack(level, boss.getX(), boss.getY() + 0.5, boss.getZ(), stack);
        }
    }

    /**
     * One roll of {@code keeper/extra_piece} for each extra copy of an unstackable stack. Rolled after
     * every other table, so a roll can be checked against every unstackable item the payout already
     * holds; a roll that repeats one is re-rolled up to {@link KeeperLootTables#MAX_EXTRA_REROLLS} times,
     * then accepted. The extras are not multiplied themselves. If the table is missing the single copy
     * stands and one warning is logged.
     */
    private static List<ItemStack> rollExtraPieces(ServerLevel level, LootParams params,
                                                   List<ItemStack> payout, int wanted) {
        if (wanted <= 0) {
            return List.of();
        }
        LootTable table = tableFor(level, KeeperLootTables.EXTRA_PIECE);
        if (table == LootTable.EMPTY) {
            return List.of();
        }
        Set<Item> held = new HashSet<>();
        payout.stream().filter(s -> s.getMaxStackSize() == 1).forEach(s -> held.add(s.getItem()));

        List<ItemStack> extras = new ArrayList<>();
        for (int i = 0; i < wanted; i++) {
            List<ItemStack> roll = KeeperLootTables.rollDistinct(
                    () -> table.getRandomItems(params),
                    candidate -> candidate.stream().anyMatch(s -> s.getMaxStackSize() == 1 && held.contains(s.getItem())),
                    KeeperLootTables.MAX_EXTRA_REROLLS);
            for (ItemStack piece : roll) {
                extras.add(piece);
                if (piece.getMaxStackSize() == 1) {
                    held.add(piece.getItem());
                }
            }
        }
        return extras;
    }

    private static LootTable tableFor(ServerLevel level, String name) {
        ResourceLocation id = new ResourceLocation(FrontierMode.MODID, "keeper/" + name);
        LootTable table = level.getServer().getLootData().getLootTable(id);
        if (table == LootTable.EMPTY) {
            OUT.warn("[Boss] KeeperLoot.pay(): no loot table " + id + " -- nothing dropped for it.");
        }
        return table;
    }

    private static ItemStack copyOf(ItemStack stack, int count) {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }
}
