package com.arryn.frontiermode.boss;

import com.arryn.frontiermode.core.EntityScales;
import com.arryn.frontiermode.boss.common.fixture.BossFixture;
import com.arryn.frontiermode.boss.common.fixture.BossSentinelsFixture;
import com.arryn.frontiermode.boss.server.commands.BossCommands;
import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * Entry point for initializing and hooking the Boss subsystem. Depends on Border -- must be
 * initialized after {@code BorderModule.init()}, never the reverse. See
 * wiki/frontiermode/architecture/boss.md for the design. The module is a root: one line per config, each config a class
 * beside its handlers ({@link BossJigConfig}, {@link BossMobJigConfig}, {@link SentinelPublicJigConfig}), the commands,
 * and the registration of {@link BossScaleSource}.
 *
 * <p><b>Known limitation:</b> a boss removed without a wired {@code LivingDeathEvent} (bare
 * {@code /kill}, external world editing, unrelated-mod bug) is not detected -- {@link BossFixture}
 * keeps {@code alive: true} and nothing here clears it. Distinguishing that from a chunk unload
 * needs either a real death signal or a "expected but absent for N ticks" heuristic; see
 * FRO_043's log for the design call.
 */
public final class BossModule {

    private BossModule() {
    }

    public static void init() {
        OUT.info("FrontierMode - Boss Module Initiating.");
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> BossIngress.build());
        Satchel.registerJig(JigPolicies.SideApplicability.BOTH, () -> BossJigConfig.build());
        Satchel.registerJig(JigPolicies.SideApplicability.BOTH, () -> BossMobJigConfig.build());
        Satchel.registerJig(JigPolicies.SideApplicability.BOTH, () -> SentinelPublicJigConfig.build());
        EntityScales.register(new BossScaleSource());
    }

    /** Registers {@code /boss} commands. */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BossCommands.register(event.getDispatcher());
    }

    /**
     * Sentinel conversion's entry point: called by {@code SpawnCoordinator}, the one consumer of
     * {@code MobSpawnFinalizing}, in its fixed order after Feral's Slime/MagmaCube deny.
     *
     * <p>Never cancels the spawn either way -- Sentinels is a modifier on an already-happening
     * spawn, not a summon. "Standby, don't crash": a {@link BossSentinelsFixture} that isn't
     * resolvable yet (Satchel not ready, scope not known) is a silent no-op -- the vanilla spawn
     * proceeds unmodified either way. {@code level} is already confirmed a real {@link ServerLevel}
     * by the caller.
     */
    public static void onMobSpawnFinalize(Mob mob, ServerLevel level) {
        BossAPI.sentinels(level).ifPresent(sentinels ->
                sentinels.onMobSpawnFinalize(mob, level));
    }
}
