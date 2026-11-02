package com.arryn.frontiermode.bosstell;

import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.bosstell.common.CompassRefresh;
import com.arryn.frontiermode.bosstell.common.items.AmethystCompassItem;
import com.arryn.frontiermode.bosstell.common.enchantments.IntuitionEnchantment;
import com.arryn.frontiermode.bosstell.common.enchantments.ResonanceEnchantment;
import com.arryn.frontiermode.bosstell.server.commands.BossTellCommands;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Entry point for {@code BossTellModule} (FRO_118, RM_FRO_033 "Marilyn") -- the Admin Compass and
 * the shared core underneath it, per
 * wiki/frontiermode/architecture/special-compass.md#lives-in-bosstellmodule-not-bossbundle-and-not-a-bordermodule-file.
 * Its own top-level module, sibling to {@code BorderModule}/{@code BossModule}/{@code FeralModule}
 * -- not folded into either, since the compass has no {@code BossBundle}-hosted fixture to reach
 * and isn't a Border-core concept (see that page's own reasoning).
 *
 * <p>Item/enchantment registration deliberately lives here, not on {@code FrontierMode.java}'s
 * shared {@code ARGUMENT_TYPES}/{@code SOUND_EVENTS} pattern -- project owner's call, per
 * special-compass.md's "Registration" section: this item belongs to the module, not the mod's
 * general cross-cutting infrastructure. {@link #ITEMS} still has to be registered onto the mod
 * event bus from {@code FrontierMode}'s constructor (Forge's {@code DeferredRegister} needs the
 * bus instance only available there), the one piece of wiring that can't live entirely inside
 * this class.
 *
 * <p>The module is a root: item and enchantment registration, the ingress and the Trinket
 * ({@link BossTellIngress}, {@code CompassRefresh}) and command registration. It registers no jig.
 */
public final class BossTellModule {

    private BossTellModule() {
    }

    // ------------------------------------------------------------
    // Item registration (lives here, not on FrontierMode.java -- see class doc)
    // ------------------------------------------------------------

    public static final DeferredRegister<net.minecraft.world.item.Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FrontierMode.MODID);

    public static final RegistryObject<net.minecraft.world.item.Item> AMETHYST_COMPASS =
            ITEMS.register("amethyst_compass",
                    () -> new AmethystCompassItem(new net.minecraft.world.item.Item.Properties()));

    // FRO_121: same reasoning as ITEMS above -- confirmed against real Forge 1.20.1-47.4.10
    // sources that ForgeRegistries.ENCHANTMENTS is a plain IForgeRegistry<Enchantment>, no
    // Keys-based DeferredRegister overload needed (unlike some other registries this version
    // moved onto Keys for).
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, FrontierMode.MODID);

    public static final RegistryObject<Enchantment> INTUITION =
            ENCHANTMENTS.register("intuition", IntuitionEnchantment::new);

    public static final RegistryObject<Enchantment> RESONANCE =
            ENCHANTMENTS.register("resonance", ResonanceEnchantment::new);

    public static void init() {
        OUT.info("FrontierMode - BossTellModule Initiating.");
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> BossTellIngress.build());
        Satchel.registerJig(JigPolicies.SideApplicability.SERVER, () -> TellJigConfig.build());
        Satchel.contributePlayerTrinket("frontiermode:bosstell", JigPolicies.SideApplicability.SERVER, Order.NORMAL,
                CompassRefresh.class, () -> new CompassRefresh());
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BossTellCommands.register(event.getDispatcher());
    }
}
