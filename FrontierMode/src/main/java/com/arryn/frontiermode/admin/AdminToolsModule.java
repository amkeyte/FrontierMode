package com.arryn.frontiermode.admin;

import com.arryn.frontiermode.FrontierMode;
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
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

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

    public static void init() {
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
    }
}
