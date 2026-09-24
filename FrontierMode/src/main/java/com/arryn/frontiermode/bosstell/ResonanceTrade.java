package com.arryn.frontiermode.bosstell;

import com.arryn.frontiermode.FrontierMode;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

/**
 * FRO_121: injects Resonance into the Librarian's enchanted-book trade pool, per
 * special-compass.md's "Registration" section -- not part of vanilla's default pool for a
 * modded enchantment, so this has to be added explicitly rather than relying on
 * {@code Enchantment.isTradeable()} (vanilla's own generic "pick any tradeable enchant"
 * listing, {@code VillagerTrades.EnchantBookForEmeralds}, is package-private and unusable from
 * mod code anyway -- checked against the real 1.20.1-47.4.10 sources). A Nether fortress chest
 * source is a planned later addition on top, not part of this ticket.
 *
 * <p>{@code VillagerTradesEvent} fires once per registered profession, on the Forge (not mod)
 * event bus, common-side (not client-only) -- trades are server-authoritative state.
 */
@Mod.EventBusSubscriber(modid = FrontierMode.MODID)
public final class ResonanceTrade {

    private ResonanceTrade() {
    }

    // Fixed price, not vanilla's own randomized enchant-book formula (this trade always offers
    // exactly Resonance, never a random pick) -- "safe baseline, replace later," same framing
    // as every other tunable in this cluster. Offered at Librarian level 3, roughly matching
    // where other treasure-tier trades sit.
    private static final int LIBRARIAN_LEVEL = 3;
    private static final int EMERALD_COST = 20;
    private static final int MAX_USES = 4;
    private static final int VILLAGER_XP = 15;
    private static final float PRICE_MULTIPLIER = 0.2F;

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.LIBRARIAN) {
            return;
        }
        event.getTrades().get(LIBRARIAN_LEVEL).add(new ResonanceListing());
    }

    private static final class ResonanceListing implements VillagerTrades.ItemListing {
        @Override
        public MerchantOffer getOffer(@NotNull Entity trader, @NotNull RandomSource rng) {
            ItemStack book = EnchantedBookItem.createForEnchantment(
                    new EnchantmentInstance(BossTellModule.RESONANCE.get(), 1));
            return new MerchantOffer(
                    new ItemStack(Items.EMERALD, EMERALD_COST),
                    ItemStack.EMPTY,
                    book,
                    MAX_USES,
                    VILLAGER_XP,
                    PRICE_MULTIPLIER);
        }
    }
}
