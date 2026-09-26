package com.arryn.frontiermode.bosstell;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.bosstell.common.items.AmethystCompassItem;
import com.arryn.frontiermode.bosstell.common.CompassAttunement;
import com.arryn.frontiermode.bosstell.common.CompassPlayerBundle;
import com.arryn.frontiermode.bosstell.common.CompassPlayerFixture;
import com.arryn.frontiermode.bosstell.common.enchantments.IntuitionEnchantment;
import com.arryn.frontiermode.bosstell.common.enchantments.ResonanceEnchantment;
import com.arryn.frontiermode.bosstell.server.commands.BossTellCommands;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.player.PlayerJig;
import com.arryn.satchel.common.jig.player.PlayerJigConfig;
import com.arryn.satchel.common.jig.player.PlayerScope;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.lifecycle.ItemPickupSignal;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
        registerCompassPlayerJig();
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BossTellCommands.register(event.getDispatcher());
    }

    // ------------------------------------------------------------
    // PlayerJig wiring -- CompassPlayerFixture's live-refresh tick.
    // Mirrors BorderModule's own BORDER_PLAYER_JIG registration exactly.
    // ------------------------------------------------------------

    private static void registerCompassPlayerJig() {
        var compassPlayerFixture =
                new JigBundles.FixtureDecl<>(
                        FrontierKeys.COMPASS_PLAYER,
                        CompassPlayerFixture::new,
                        JigPolicies.CreatePolicy.ALWAYS
                );

        var compassPlayerBundle =
                new JigBundles.BundleDecl<>(
                        FrontierKeys.COMPASS_PLAYER_BUNDLE,
                        (PlayerScope scope) -> new CompassPlayerBundle(scope, FrontierKeys.COMPASS_PLAYER_BUNDLE),
                        List.of(compassPlayerFixture)
                );

        JigBundles.Schema<PlayerScope> schema =
                new JigBundles.Schema<>(List.of(compassPlayerBundle));

        // FRO_118 ownership piece, unblocked now that SAT_072 landed: ItemPickupSignal is a bare,
        // unscoped signal posted to the whole SatchelEventBus (not routed per-jig) -- declaring
        // its handler on this JigConfig's own eventHandlers is only where installation happens
        // to occur at registration time, the same precedent BossModule already established for
        // MobDied on BOSS_JIG's eventHandlers (see BossModule.init() and EventHandlers.install()'s
        // own doc: handlers just get subscribed onto the shared bus, regardless of which jig's
        // config declared them).
        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(ScopeEvent.Tick.class, BossTellModule::onCompassPlayerTick)
                        .on(ItemPickupSignal.class, BossTellModule::onItemPickup)
                        .build();

        // Not persisted, not networked -- CompassPlayerFixture is a live-recomputed refresh
        // driver, same "no capabilities()/persistence() override needed" shape
        // BorderPlayerStatusFixture/BORDER_PLAYER_JIG already establish.
        PlayerJigConfig config = new PlayerJigConfig(FrontierKeys.COMPASS_PLAYER_JIG);
        config.bundles().schema(schema);
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults().withTick(true))
                .eventHandlers(eventHandlers);

        Satchel.registerJigConfig(config);
    }

    /**
     * Shared-bus caveat, same as every other {@code ScopeEvent} handler in this codebase --
     * {@code ScopeEvent.Tick} fires for every jig scoped to whatever just ticked, so the
     * {@code COMPASS_PLAYER_JIG} key check is what keeps this from running against the wrong
     * jig's scopes.
     */
    private static void onCompassPlayerTick(ScopeEvent.Tick event) {
        ScopeInfo info = event.info();
        Objects.requireNonNull(info, "info");

        if (!FrontierKeys.COMPASS_PLAYER_JIG.equals(info.jigInfo().key)) {
            return;
        }

        var jig = (PlayerJig) info.jigInfo().jig;
        PlayerScope scope = (PlayerScope) info.scope();
        ServerPlayer player = (ServerPlayer) scope.player();

        CompassPlayerBundle bundle = jig.getOrCreate(scope, FrontierKeys.COMPASS_PLAYER_BUNDLE);
        CompassPlayerFixture fixture =
                bundle.getOrCreateFixture(FrontierKeys.COMPASS_PLAYER, CompassPlayerFixture::new);

        fixture.tick(player);
    }

    // ------------------------------------------------------------
    // Ownership veto (FRO_118, unblocked by SAT_072's ItemPickupSignal).
    // ------------------------------------------------------------

    /**
     * "Blocked, never destroyed" for anyone but the compass's owner, per
     * special-compass.md#playerscope-fixture-live-refresh-not-the-pickup-block -- cancels the
     * pickup outright for any player who isn't the stack's recorded owner. Cancelling
     * {@code EntityItemPickupEvent} doesn't remove the {@code ItemEntity} or animate anything;
     * it's left exactly where it was, still pickable on a future attempt (by the rightful owner,
     * including from their own death drop) -- no explicit "leave it alone" logic needed here
     * beyond simply not calling {@link ItemPickupSignal#cancel()} for the true owner. A stack
     * with no attunement at all (an ordinary vanilla compass, or any other item) is untouched.
     */
    private static void onItemPickup(ItemPickupSignal signal) {
        ItemStack stack = signal.item().getItem();
        if (!(stack.getItem() instanceof AmethystCompassItem)) {
            return;
        }

        Optional<UUID> ownerId = CompassAttunement.ownerId(stack);
        if (ownerId.isEmpty()) {
            return;
        }

        if (!ownerId.get().equals(signal.player().getUUID())) {
            signal.cancel();
        }
    }
}
