package com.arryn.frontiermode.admin;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.FrontierMode;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.lifecycle.LivingDropsSignal;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entry point for a small admin/debug tool set -- project owner request this session: an
 * enchant that kills whatever it hits, and a stick to carry it. Its own top-level module,
 * sibling to {@code BossTellModule}/{@code StructureModule} -- not boss/border/structure
 * content, so it doesn't belong in any of those.
 *
 * <p>{@link #onLivingHurt} is common-side (not client-only), registered on the default (Forge)
 * bus via {@code @Mod.EventBusSubscriber} -- damage resolution is server-authoritative, and this
 * needs to run before the normal damage pipeline finishes, the same "before it completes, not a
 * side effect after" shape SAT_072's own pickup-veto signal uses, just via a raw vanilla event
 * instead of a Satchel signal (no existing ingress wraps combat damage).
 */
public final class AdminToolsModule {

    private AdminToolsModule() {
    }

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, FrontierMode.MODID);

    public static final RegistryObject<Enchantment> DEATH_TOUCH =
            ENCHANTMENTS.register("death_touch", DeathTouchEnchantment::new);

    /**
     * FRO_126: stacks removed from a Death Stick death drop aren't preserved anywhere by that
     * removal alone -- stashed here, keyed by the dying player's UUID, and handed back in
     * {@link Listener#onPlayerRespawn}. A plain static map, not persisted -- same "not surviving
     * a server crash between death and respawn is an acceptable gap" precedent as every other
     * in-memory tracking map in this codebase (e.g. {@code FeralModule}'s own throttler maps); a
     * respawn always follows a death within the same server session in practice. Lives on the
     * outer class, not {@link Listener}, since {@link #onLivingDrops} (the writer) is no longer a
     * raw Forge subscriber -- {@link Listener#onPlayerRespawn} (the reader) still reaches it as
     * an enclosing-class private field, same as before.
     */
    private static final Map<UUID, List<ItemStack>> SOULBOUND_STASH = new HashMap<>();

    public static void init() {
        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(LivingDropsSignal.class, AdminToolsModule::onLivingDrops)
                        .build();

        // FRO_149: exists purely to get a signal subscription point -- no bundle/fixture, no tick
        // concern (overrides LevelJigConfig's own withTick(true) default back to false, unlike
        // FERAL_LEVEL_JIG, which genuinely ticks).
        LevelJigConfig config = new LevelJigConfig(FrontierKeys.ADMIN_TOOLS_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of()));
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults())
                .eventHandlers(eventHandlers);

        Satchel.registerJigConfig(config);
    }

    /**
     * FRO_149: rides {@link LivingDropsSignal} (SAT_072) instead of a raw Forge subscription --
     * {@code signal.drops()} is the same live, mutable collection
     * {@code event.getDrops()} used to expose. FRO_126 (soulbound, "survives death"): pulls any
     * Death Stick out of the death drop list before it becomes a lootable {@code ItemEntity},
     * stashing it in {@link #SOULBOUND_STASH} for {@link Listener#onPlayerRespawn} to hand back --
     * vanilla's own death handling has already cleared the player's real inventory by this point,
     * so this is the only place left to intercept it.
     */
    private static void onLivingDrops(LivingDropsSignal signal) {
        if (!(signal.entity() instanceof ServerPlayer player)) {
            return;
        }

        List<ItemStack> stashed = new ArrayList<>();
        signal.drops().removeIf(itemEntity -> {
            ItemStack stack = itemEntity.getItem();
            if (EnchantmentHelper.getItemEnchantmentLevel(DEATH_TOUCH.get(), stack) <= 0) {
                return false;
            }
            stashed.add(stack);
            return true;
        });

        if (!stashed.isEmpty()) {
            SOULBOUND_STASH.put(player.getUUID(), stashed);
        }
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("admin")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("deathstick")
                                .executes(AdminToolsModule::giveDeathStick))
        );
    }

    /**
     * Hands the caller a plain vanilla stick, pre-enchanted with Death Touch and named so it
     * reads clearly as the admin tool it is -- direct construction, the same "never through the
     * recipe book or crafting grid" shape {@code /boss debug compass} already uses for the
     * Admin Compass. No creative-tab entry, no recipe -- {@code /give} would work too (once the
     * enchant is known to exist), but wouldn't apply the enchant itself.
     */
    private static int giveDeathStick(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();

        ItemStack stick = new ItemStack(Items.STICK);
        stick.enchant(DEATH_TOUCH.get(), 1);
        stick.setHoverName(Component.literal("Death Stick"));

        if (!sp.getInventory().add(stick)) {
            sp.drop(stick, false);
        }

        ctx.getSource().sendSuccess(() -> Component.literal("[Admin] Gave a Death Stick."), false);
        return 1;
    }

    /**
     * Any hit landed with a weapon carrying Death Touch overwhelms the target's health outright
     * -- {@code setAmount(Float.MAX_VALUE)} rather than calling {@code target.kill()} directly
     * from inside this handler, so the kill still runs through the normal, already-in-progress
     * damage pipeline (armor/absorption/death handling) instead of a re-entrant second call
     * stack on top of it. {@code DamageSource.getEntity()} is the true causer (the attacker
     * itself for an ordinary melee hit, as opposed to {@code getDirectEntity()}, which would be
     * a thrown projectile instead) -- checked against their mainhand item specifically, since
     * that's what actually landed the hit.
     */
    @Mod.EventBusSubscriber(modid = FrontierMode.MODID)
    public static final class Listener {
        private Listener() {
        }

        @SubscribeEvent
        public static void onLivingHurt(LivingHurtEvent event) {
            if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
                return;
            }
            if (EnchantmentHelper.getItemEnchantmentLevel(DEATH_TOUCH.get(), attacker.getMainHandItem()) <= 0) {
                return;
            }
            event.setAmount(Float.MAX_VALUE);
        }

        /**
         * FRO_126 (soulbound, "can't be dropped"): cancels the toss and hands the stack straight
         * back to the player's own inventory rather than just cancelling and trusting the item
         * stays put -- {@code Player#drop} already removed it from the inventory slot by the time
         * this event fires, so cancelling alone would silently delete it (removed from inventory,
         * never added to the world) instead of actually preventing the drop.
         */
        @SubscribeEvent
        public static void onItemToss(ItemTossEvent event) {
            if (!(event.getPlayer() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack stack = event.getEntity().getItem();
            if (EnchantmentHelper.getItemEnchantmentLevel(DEATH_TOUCH.get(), stack) <= 0) {
                return;
            }

            event.setCanceled(true);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }

        /**
         * FRO_126: hands back whatever {@link AdminToolsModule#onLivingDrops} stashed for this
         * player, if anything.
         */
        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            List<ItemStack> stashed = SOULBOUND_STASH.remove(player.getUUID());
            if (stashed == null) {
                return;
            }

            for (ItemStack stack : stashed) {
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        }
    }
}
