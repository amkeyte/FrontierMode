package com.arryn.satchel.common.jig.host;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigConfigCompiler;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.level.LevelJig;
import com.arryn.satchel.common.jig.level.LevelJigConfig;
import com.arryn.satchel.common.jig.player.PlayerJig;
import com.arryn.satchel.common.jig.player.PlayerJigConfig;
import com.arryn.satchel.common.trinket.Trinket;

import java.util.List;
import java.util.function.Supplier;

/**
 * Satchel's host jigs: one per scope kind with a module-facing host, a <b>level host</b> and a
 * <b>player host</b>. A host jig has no bundles, applies to both sides and ticks. It holds Trinkets
 * and nothing else, so a module that has no jig of its own can still get per-scope runtime behavior
 * and a tick without registering a jig for it.
 *
 * <p>
 * A module contributes a Trinket at mod init, naming itself, the side or sides it exists on, its
 * {@code order} and its type and factory ({@link Satchel#contributeLevelTrinket}, {@link
 * Satchel#contributePlayerTrinket}). For every ready scope on a side the contribution names, Satchel
 * creates the Trinket and drops it at unload. Contributions freeze with the jig configs: one made
 * after compilation has begun fails at boot.
 *
 * <p>
 * A host Trinket's existence says only that the scope is ready: the host has no bundle that could
 * say more. A Trinket that depends on a module's own bundles on another jig gates itself with {@code
 * Trinket#isReady(ScopeInfo)}.
 *
 * <p>
 * Looked up like any Trinket, with the host's key: {@code Satchel.trinket(HostJigs.LEVEL_HOST, scope,
 * Type.class)}, usually behind the module's own resolver. A lookup of a type the host holds only on
 * the other side throws, a programming error, as any undeclared type does.
 */
public final class HostJigs {

    private HostJigs() {
    }

    public static final JigKey<LevelJig> LEVEL_HOST =
            new JigKey<>("satchel:level_host", LevelJig.class);

    public static final JigKey<PlayerJig> PLAYER_HOST =
            new JigKey<>("satchel:player_host", PlayerJig.class);

    private static LevelJigConfig levelConfig;
    private static PlayerJigConfig playerConfig;
    private static boolean registered;

    /**
     * Registers the two host jigs with the compiler, once, before any foundation boots. Called from
     * Satchel's mod setup, ahead of every jig Satchel's own modules register, so the hosts come first
     * in registration order.
     */
    public static synchronized void register() {
        if (registered) return;
        registered = true;

        Satchel.registerJigConfig(level());
        Satchel.registerJigConfig(player());
    }

    // =================================================================
    // Contributions
    // =================================================================

    public static synchronized <T extends Trinket> void contributeToLevels(
            String moduleId,
            JigPolicies.SideApplicability side,
            int order,
            Class<T> type,
            Supplier<? extends T> factory
    ) {
        requireOpen(moduleId, type);
        level().trinkets().contribute(moduleId, side, order, type, factory);
    }

    public static synchronized <T extends Trinket> void contributeToPlayers(
            String moduleId,
            JigPolicies.SideApplicability side,
            int order,
            Class<T> type,
            Supplier<? extends T> factory
    ) {
        requireOpen(moduleId, type);
        player().trinkets().contribute(moduleId, side, order, type, factory);
    }

    private static void requireOpen(String moduleId, Class<?> type) {
        if (JigConfigCompiler.isFrozen()) {
            throw new IllegalStateException(
                    "Late Trinket contribution from " + moduleId + " (" + type.getName() + "): the jig "
                            + "configs have already compiled. Contribute at mod init.");
        }
    }

    // =================================================================
    // The configs, built on first use so a contribution made before Satchel's own setup has
    // somewhere to go.
    // =================================================================

    private static LevelJigConfig level() {
        if (levelConfig == null) {
            LevelJigConfig c = new LevelJigConfig(LEVEL_HOST);
            c.binding().sideApplicability(JigPolicies.SideApplicability.BOTH);
            c.bundles().schema(new JigBundles.Schema<>(List.of()));
            c.execution().lifecycle(JigPolicies.Lifecycle.defaults().withTick(true));
            levelConfig = c;
        }
        return levelConfig;
    }

    private static PlayerJigConfig player() {
        if (playerConfig == null) {
            PlayerJigConfig c = new PlayerJigConfig(PLAYER_HOST);
            c.binding().sideApplicability(JigPolicies.SideApplicability.BOTH);
            c.bundles().schema(new JigBundles.Schema<>(List.of()));
            c.execution().lifecycle(JigPolicies.Lifecycle.defaults().withTick(true));
            playerConfig = c;
        }
        return playerConfig;
    }
}
