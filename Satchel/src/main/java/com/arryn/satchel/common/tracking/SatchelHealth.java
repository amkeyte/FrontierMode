package com.arryn.satchel.common.tracking;

import com.arryn.satchel.common.config.SatchelConfig;
import com.arryn.satchel.common.identity.WorldIdentityContext;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SAT_039/SAT_040/SAT_060 -- the permanent home for every run-monitoring / self-verification
 * module Satchel owns, not a single-purpose test: an automatic, silent-on-pass, loud-on-fail
 * regression backstop proving each jig kind's teardown only ever happens for a reason its own
 * contract actually allows. Split by jig kind across four files (SAT_060, once this file grew
 * too large to safely edit as one piece) -- {@link MobHealthCheck}, {@link LevelHealthCheck},
 * and {@link PlayerHealthCheck} each own one jig kind's keys, registration, and event handlers;
 * this file holds only what's genuinely shared across all three and what code outside this
 * package actually calls: {@link #init()}, the {@link #watch}/{@link #unwatch}/{@link
 * #currentInterests} surface {@code MobTrackCommands} uses, and the {@link
 * #defer}/{@link #pumpPendingHealthChecks} deferred-check queue {@code
 * LevelHealthCheck}/{@code PlayerHealthCheck} enqueue onto and {@code ServerForgeIngress} drains.
 * Same package, same conceptual "one home" as before the split -- just not one literal file.
 *
 * <p><b>MobJig, LevelJig, and PlayerJig are not the same shape underneath, and their checks
 * aren't copy-pasted from one another.</b> MobJig's teardown is <i>inferred</i> -- a poll-driven
 * reconcile cycle (~every 20 ticks) decides a scope is gone based on whether the backing entity
 * can still be resolved, which is exactly the kind of decision that can be wrong (RM_SAT_022's
 * whole premise). LevelJig and PlayerJig's teardown is <i>authoritative</i> -- {@code
 * ServerForgeIngress}/{@code ClientForgeIngress} call {@code tryRemoveSource} from exactly one
 * real Forge event each ({@code LevelEvent.Unload}, {@code PlayerLoggedOutEvent}), never inferred.
 * So their violation checks aren't "is the backing thing still there" (Mob's shape) but "did
 * teardown get triggered while the backing thing is still demonstrably live" -- catching a future
 * regression that calls {@code tryRemoveSource} from the wrong place. See each file's own
 * violation-check javadoc for the reasoning specific to that jig kind.
 *
 * <p><b>Shape: a live-run monitor, not a build-time (JUnit/GameTest) test.</b> Project owner's
 * explicit call: what's wanted is a permanent, in-repo "SatchelHealth" module that proves a
 * major service each jig kind provides hasn't broken, checked every time someone actually runs a
 * client/server, not gated behind {@code gradle test}. That trades CI-style gating (nothing stops
 * a broken commit from landing unnoticed if nobody runs the client that session) for reaching
 * something JUnit-without-Forge fundamentally can't: a live entity resolution path.
 *
 * <p><b>Known limitation, LevelJig:</b> the violation check only runs server-side, using {@code
 * MinecraftServer#getLevel} to ask whether the dimension is still actively registered. There's no
 * client-side equivalent here -- the only clean signal would be {@code
 * Minecraft.getInstance().level}, and per this project's Forge Integration & Sidedness Contract,
 * {@code ClientForgeIngress} is the only class meant to touch client-engine state directly; adding
 * that reference here would break that boundary for a check whose value hasn't been weighed
 * against the cost. {@code LevelJig}'s config is still widened to {@code BOTH}, so the client-side
 * load/tick/unload path is genuinely exercised -- an exception anywhere in it still crashes loudly
 * -- just not this specific violation check.
 *
 * <p><b>Crash is unconditional whenever this module is enabled at all</b> -- see {@link
 * SatchelConfig#HEALTH_ENABLED} (SAT_060) for the real switch: {@code false} means {@link
 * #init()} registers none of the three jig configs, so no tracker bundle, no violation check,
 * and no synthetic exercise of any kind runs at all. Intended for a release/production profile,
 * or whenever this module's synthetic activity is unwanted -- there is no finer-grained
 * per-check gate, on purpose; this is an all-or-nothing switch.
 */
public final class SatchelHealth {

    private SatchelHealth() {
    }

    // =================================================================
    // Per-world state. Everything this module holds that belongs to one running world lives in a
    // HealthState, found by the world's identity token (WorldIdentityContext -- side-bound, so
    // this stays common code with no server type in it), and is dropped when that server stops:
    // an integrated server restarts inside one JVM, so state kept anywhere longer-lived is
    // inherited by the next one. This map is the one anchor.
    // =================================================================

    private static final Map<UUID, HealthState> STATES = new ConcurrentHashMap<>();

    /**
     * The state of the world the calling thread's side is in, if it has any yet.
     */
    private static Optional<HealthState> existing() {
        return WorldIdentityContext.current().map(STATES::get);
    }

    /**
     * The state of the world the calling thread's side is in, created on first use.
     *
     * @throws IllegalStateException before that side has a world identity -- a caller on a bound
     *         thread with a world loaded always has one
     */
    static HealthState state() {
        UUID world = WorldIdentityContext.current().orElseThrow(() -> new IllegalStateException(
                "SatchelHealth state requested before the world identity token is bound"));
        return STATES.computeIfAbsent(world, k -> new HealthState());
    }

    /**
     * Called before a server creates any level. Only one server runs per JVM, so anything still
     * held is the leftover of one that ended without a stop event (a crash).
     */
    public static void onServerAboutToStart() {
        STATES.clear();
    }

    /**
     * Called after a server's last level has unloaded: drops its state, including any deferred
     * check its shutdown queued that can never be pumped now.
     */
    public static void onServerStopped() {
        STATES.clear();
    }

    // =================================================================
    // Manual interest -- the surface MobTrackCommands uses, and the supplier MobJig's health
    // registration answers its per-level question from.
    // =================================================================

    /**
     * Registers manual interest in {@code uuid} within {@code level}.
     */
    public static void watch(Level level, UUID uuid) {
        state().interests.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet()).add(uuid);
    }

    public static void unwatch(Level level, UUID uuid) {
        existing().ifPresent(state -> {
            Set<UUID> set = state.interests.get(level);
            if (set != null) {
                set.remove(uuid);
            }
        });
    }

    public static Map<Level, Set<UUID>> currentInterests() {
        return existing().<Map<Level, Set<UUID>>>map(state -> new HashMap<>(state.interests))
                .orElse(Map.of());
    }

    /**
     * This world's canary, once it has spawned one.
     */
    static Optional<UUID> canaryUuid() {
        return existing().map(state -> state.canaryUuid);
    }

    /**
     * The per-level interest answer: a live view of the watched set, empty for a level nothing is
     * watched in. Runs on every mob death and every mob leaving a level (see {@code
     * MobInterestPerLevel}), so it is two lookups and no copying.
     */
    static Set<UUID> interestsIn(Level level) {
        return existing().map(state -> state.interests.get(level)).orElse(Set.of());
    }

    // =================================================================
    // Registration
    // =================================================================

    public static void init() {
        if (!SatchelConfig.HEALTH_ENABLED.get()) {
            OUT.info("SatchelHealth disabled via config (healthEnabled=false) -- not registering.");
            return;
        }

        OUT.info(
                "SatchelHealth Initializing (SAT_039/SAT_040/SAT_060 -- MobJig [BOTH], LevelJig "
                        + "[BOTH], PlayerJig [SERVER])"
        );

        MobHealthCheck.register();
        LevelHealthCheck.register();
        PlayerHealthCheck.register();
        TrinketHealthCheck.register();
    }

    // =================================================================
    // Deferred violation checks -- LevelHealthCheck/PlayerHealthCheck defer onto the server's
    // HealthState from their own onXScopeUnloaded handlers; drained once per real server tick from
    // ServerForgeIngress#onExecutionPulse, a genuinely later call chain than the one that queued
    // each check. Plain ArrayDeque, not a concurrent collection: every defer happens from a Forge
    // event fired on the server thread, and the only drain point also runs on the server thread.
    // =================================================================

    static void defer(Runnable check) {
        state().pendingChecks.add(check);
    }

    /**
     * Called once per server tick by {@code ServerForgeIngress#onExecutionPulse}. Drains and runs
     * every check queued since the last pump. Drains the whole queue every call rather than just
     * the head -- these checks are cheap (a single map/list lookup each) and there's no reason to
     * spread them across multiple ticks.
     */
    public static void pumpPendingHealthChecks() {
        existing().ifPresent(state -> {
            Runnable check;
            while ((check = state.pendingChecks.poll()) != null) {
                check.run();
            }
        });
    }
}
