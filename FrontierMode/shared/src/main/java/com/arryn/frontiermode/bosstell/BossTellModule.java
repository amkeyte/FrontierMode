package com.arryn.frontiermode.bosstell;

import com.arryn.frontiermode.bosstell.common.CompassRefresh;
import com.arryn.frontiermode.bosstell.common.enchantments.IntuitionEnchantment;
import com.arryn.frontiermode.bosstell.common.enchantments.ResonanceEnchantment;
import com.arryn.frontiermode.bosstell.server.commands.BossTellCommands;
import com.arryn.frontiermode.core.FrontierIds;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.item.enchantment.Enchantment;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Entry point for {@code BossTellModule} -- the Admin Compass and
 * the shared core underneath it, per
 * wiki/frontiermode/architecture/special-compass.md#lives-in-bosstellmodule-not-bossbundle-and-not-a-bordermodule-file.
 * Its own top-level module, sibling to {@code BorderModule}/{@code BossModule}/{@code FeralModule}
 * -- not folded into either, since the compass has no {@code BossBundle}-hosted fixture to reach
 * and isn't a Border-core concept (see that page's own reasoning).
 *
 * <p>Item and entity-type registration lives in {@link BossTellRegisters}, not on the mod's own
 * {@code FrontierRegisters} -- project owner's call, per special-compass.md's "Registration" section: this item
 * belongs to the module, not the mod's general cross-cutting infrastructure. {@link #ENCHANTMENTS} still has to be
 * registered onto the mod event bus from the line's {@code FrontierMode} constructor.
 *
 * <p>The module is a root: enchantment registration, the ingress and the Trinket
 * ({@link BossTellIngress}, {@code CompassRefresh}) and command registration. It registers no jig.
 */
public final class BossTellModule {

    private BossTellModule() {
    }

    // ------------------------------------------------------------
    // Enchantment registration (items and the entity type are on BossTellRegisters)
    // ------------------------------------------------------------

    // Confirmed against real Forge 1.20.1-47.4.10
    // sources that ForgeRegistries.ENCHANTMENTS is a plain IForgeRegistry<Enchantment>, no
    // Keys-based DeferredRegister overload needed (unlike some other registries this version
    // moved onto Keys for).
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, FrontierIds.MOD_ID);

    public static final RegistryObject<Enchantment> INTUITION =
            ENCHANTMENTS.register("intuition", IntuitionEnchantment::new);

    public static final RegistryObject<Enchantment> RESONANCE =
            ENCHANTMENTS.register("resonance", ResonanceEnchantment::new);

    public static void init() {
        OUT.info("[BossTell] init(): Module initiating.");
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> BossTellIngress.build());
        Satchel.registerJig(JigPolicies.SideApplicability.SERVER, () -> TellJigConfig.build());
        Satchel.contributePlayerTrinket("frontiermode:bosstell", JigPolicies.SideApplicability.SERVER, Order.NORMAL,
                CompassRefresh.class, () -> new CompassRefresh());
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        BossTellCommands.register(dispatcher);
    }
}
