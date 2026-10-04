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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * A defeated Keeper's reward (boss.md § Defeat loot): the vanilla-format tables under
 * {@code frontiermode:keeper/} that {@link KeeperLootTables} names, rolled with the credited player as
 * the killer and dropped as real items at the death position, so the reward is something a player walks
 * over and picks up. The body's own vanilla drops are untouched: they come from the death itself, with
 * Looting, and are where an Evoker Keeper's Totem of Undying comes from.
 */
final class KeeperLoot {

    private KeeperLoot() {
    }

    /**
     * Pays {@code record}'s defeat reward. Only reached for a counted, player-credited defeat that is the
     * last standing on its border; never from {@code BossAPI.forceDefeat} (an admin command
     * force-defeating bosses for loot is the riskless exploit this reward shouldn't have).
     */
    static void pay(ServerLevel level, Mob boss, BossRecord record, ServerPlayer credited) {
        int waveLength = BorderRules.ACTIVE.borderWaveLength();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, boss)
                .withParameter(LootContextParams.ORIGIN, boss.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, boss.damageSources().playerAttack(credited))
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, credited)
                .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, credited)
                .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, credited)
                .create(LootContextParamSets.ENTITY);

        for (String name : KeeperLootTables.names(record.pathPosition(), record.body(), waveLength)) {
            ResourceLocation id = new ResourceLocation(FrontierMode.MODID, "keeper/" + name);
            LootTable table = level.getServer().getLootData().getLootTable(id);
            if (table == LootTable.EMPTY) {
                OUT.warn("[Boss] KeeperLoot.pay(): no loot table " + id + " -- nothing dropped for it.");
                continue;
            }
            table.getRandomItems(params).forEach(stack -> Containers.dropItemStack(
                    level, boss.getX(), boss.getY() + 0.5, boss.getZ(), stack));
        }
    }
}
