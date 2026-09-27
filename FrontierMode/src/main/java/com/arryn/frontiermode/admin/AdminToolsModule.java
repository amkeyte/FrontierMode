package com.arryn.frontiermode.admin;

import com.arryn.frontiermode.FrontierKeys;
import com.arryn.frontiermode.FrontierMode;
import com.arryn.frontiermode.admin.common.enchantments.DeathTouchEnchantment;
import com.arryn.frontiermode.admin.common.enchantments.InspectEnchantment;
import com.arryn.frontiermode.admin.server.commands.AdminCommands;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.lifecycle.LivingDropsSignal;
import com.arryn.satchel.common.lifecycle.PlayerAttackSignal;
import com.arryn.satchel.common.lifecycle.PlayerRespawnSignal;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * Entry point for the small admin/debug tool set: a plain stick carrying an admin-only enchantment,
 * handed out by {@code /admin}. Its own top-level module, sibling to {@code BossTellModule} and
 * {@code StructureModule} -- not boss, border or structure content. A root and nothing more: the
 * enchantment registers, the {@code /admin} command registration, and the signal-handler installs.
 * Handler bodies are {@link AdminToolHandlers}; the report is {@code InspectReport}. See wiki
 * admin-tools.md.
 */
public final class AdminToolsModule {

    private AdminToolsModule() {
    }

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, FrontierMode.MODID);

    public static final RegistryObject<Enchantment> DEATH_TOUCH =
            ENCHANTMENTS.register("death_touch", DeathTouchEnchantment::new);

    public static final RegistryObject<Enchantment> INSPECT =
            ENCHANTMENTS.register("inspect", InspectEnchantment::new);

    public static void init() {
        // Bare, unscoped signals -- installed on this config only because it is where installation
        // happens to occur, not a claim they are scoped to it.
        EventHandlers eventHandlers =
                EventHandlers.builder()
                        .on(LivingDropsSignal.class, AdminToolHandlers::onLivingDrops)
                        .on(PlayerRespawnSignal.class, AdminToolHandlers::onPlayerRespawn)
                        .on(PlayerAttackSignal.class, AdminToolHandlers::onPlayerAttack)
                        .build();

        // Exists purely to get a signal subscription point: no bundle or fixture, and no tick
        // (overrides LevelJigConfig's own withTick(true) default back to false).
        LevelJigConfig config = new LevelJigConfig(FrontierKeys.ADMIN_TOOLS_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of()));
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults())
                .eventHandlers(eventHandlers);

        Satchel.registerJigConfig(config);

        registerViewJig();
    }

    /**
     * The client-side debug view's handler home: no bundle, BOTH applicability, only so the tick
     * handler is installed on the client (see {@code AdminViewHandlers}).
     */
    private static void registerViewJig() {
        LevelJigConfig config = new LevelJigConfig(FrontierKeys.ADMIN_VIEW_JIG);
        config.binding().sideApplicability(JigPolicies.SideApplicability.BOTH);
        config.bundles().schema(new JigBundles.Schema<>(List.of()));
        config.execution()
                .lifecycle(JigPolicies.Lifecycle.defaults())
                .eventHandlers(EventHandlers.builder()
                        .on(ScopeEvent.Tick.class, AdminViewHandlers::onTick)
                        .build());

        Satchel.registerJigConfig(config);
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        AdminCommands.register(event.getDispatcher());
    }
}
