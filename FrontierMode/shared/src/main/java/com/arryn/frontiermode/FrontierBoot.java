package com.arryn.frontiermode;

import com.arryn.frontiermode.admin.AdminToolsModule;
import com.arryn.frontiermode.border.BorderModule;
import com.arryn.frontiermode.border.server.commands.BorderSelectorArgumentType;
import com.arryn.frontiermode.border.server.commands.BorderSelectorArgumentTypeInfo;
import com.arryn.frontiermode.boss.BossModule;
import com.arryn.frontiermode.boss.server.commands.BossSelectorArgumentType;
import com.arryn.frontiermode.boss.server.commands.BossSelectorArgumentTypeInfo;
import com.arryn.frontiermode.bosstell.BossTellModule;
import com.arryn.frontiermode.bosstell.BossTellRegisters;
import com.arryn.frontiermode.coordination.server.SpawnCoordinator;
import com.arryn.frontiermode.core.CoreModule;
import com.arryn.frontiermode.difficulty.DifficultyModule;
import com.arryn.frontiermode.diorite.DioriteRegisters;
import com.arryn.frontiermode.effects.EffectsMod;
import com.arryn.frontiermode.feral.FeralModule;
import com.arryn.frontiermode.keeper.KeeperModule;
import com.arryn.frontiermode.keeper.KeeperRegisters;
import com.arryn.frontiermode.sentinel.SentinelModule;
import com.arryn.frontiermode.siting.SitingModule;
import com.arryn.frontiermode.structure.StructureModule;
import com.arryn.frontiermode.structure.StructureRegisters;
import com.arryn.satchel.common.platform.PlatformBound;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;

/**
 * The mod's start-up sequence, written once for both lines (Two Lines § The mod entry). Each line's {@code FrontierMode}
 * calls {@link #init()} from its constructor and {@link #commonSetup()} from its common-setup listener.
 */
@PlatformBound(reason = "commands")
public final class FrontierBoot {

    private FrontierBoot() {
    }

    /**
     * Loads the registers classes, installs the log filters and runs each module's {@code init()}. The line's mod
     * entry has installed the registrar by now, which a registers class needs to load. The rule (Module Graph § Init order): a module's
     * {@code init()} runs after the {@code init()} of every module it registers with. Modules on one line may run in
     * any order among themselves.
     */
    public static void init() {
        OUT.info("[FrontierMode] FrontierBoot.init(): Module initialization started.");

        // A registers class registers as it loads, so each is loaded here, before the loader fires its registry events.
        FrontierRegisters.load();
        BossTellRegisters.load();
        DioriteRegisters.load();
        KeeperRegisters.load();
        StructureRegisters.load();

        // silence vanilla's per-death log line for our own named mobs (Feral, Boss).
        LogFilters.install();

        // 1. core, where code lives that two or more modules use, and effects, the cross-cutting particle and
        // sound dispatch. Neither depends on a module; the modules that register a scale source or route an effect
        // depend on them.
        CoreModule.init();
        EffectsMod.init();

        // 2. Border: Boss, Keeper, Siting and Difficulty depend on it, never the reverse.
        BorderModule.init();

        // 3. Boss, which depends on Border; Siting and Difficulty, which depend on Border for geometry and the
        // ambient difficulty lookup.
        BossModule.init();
        SitingModule.init();
        DifficultyModule.init();

        // 4. The Keeper, the Boss base's first kind. Depends on Border and Boss.
        KeeperModule.init();

        // 5. The Sentinel depends on the Keeper (and through it on Boss); the Amethyst compass and the Cairn read
        // BossAPI and BorderAPI from outside.
        SentinelModule.init();
        BossTellModule.init();
        StructureModule.init();

        // 6. Feral mobs and admin tools have no init-order dependency on Border or Boss.
        FeralModule.init();
        AdminToolsModule.init();

        // 7. The one consumer of the mob-spawn-finalizing event: fans out to Boss, Border and Feral in a fixed
        // order. Depends on all three and none depends on it, so it inits last.
        SpawnCoordinator.init();
    }

    /**
     * Binds the two command argument types to their classes. The line's common-setup listener calls it inside the
     * loader's enqueue-work, once, on both sides.
     */
    @SuppressWarnings("unchecked")
    public static void commonSetup() {
        ArgumentTypeInfos.registerByClass(
                BorderSelectorArgumentType.class,
                (ArgumentTypeInfo<BorderSelectorArgumentType, BorderSelectorArgumentTypeInfo.Template>)
                        FrontierRegisters.BORDER_SELECTOR.get()
        );
        ArgumentTypeInfos.registerByClass(
                BossSelectorArgumentType.class,
                (ArgumentTypeInfo<BossSelectorArgumentType, BossSelectorArgumentTypeInfo.Template>)
                        FrontierRegisters.BOSS_SELECTOR.get()
        );
    }
}
