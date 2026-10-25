package com.arryn.satchel;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.guts.*;
import com.arryn.satchel.common.jig.config.JigConfig;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.IngressRegistry;
import com.arryn.satchel.common.jig.config.JigConfigCompiler;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.host.HostJigs;
import com.arryn.satchel.common.jig.mob.MobJig;
import com.arryn.satchel.common.trinket.Trinket;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Primary public access point for Satchel.
 * <p>
 * Responsibilities:
 * - Route calls to the correct LogicalFoundation
 * - Expose semantic bundle access (ask / get / getOrCreate)
 * - Provide scopeLifecycle and availability queries
 * <p>
 * Does NOT:
 * - Track execution context
 * - Infer scopeInfo
 * - Own engines or jigs
 */
public final class Satchel {

    private static final Map<LogicalSide, LogicalFoundation> FOUNDATIONS =
            new EnumMap<>(LogicalSide.class);

    private Satchel() {
    }

    /* =============================================================
     * Foundation routing
     * ========================================================== */


    public static LogicalFoundation require() {
        LogicalSide side = LogicalSideContext.require();
        LogicalFoundation foundation = FOUNDATIONS.get(side);

        if (foundation == null) {
            throw new IllegalStateException(
                    "Satchel not installed for logical side: " + side
            );
        }

        return foundation;
    }

    public static Optional<LogicalFoundation> foundation() {
        Optional<LogicalSide> sideOpt = LogicalSideContext.current();
        return sideOpt.map(FOUNDATIONS::get);
    }

    /**
     * The foundation bound to the calling thread, or null if none is: {@link #foundation()} without
     * the allocation, for the guard that runs on every Forge event a module listens to.
     */
    public static LogicalFoundation peekFoundation() {
        LogicalSide side = LogicalSideContext.peek();
        return side == null ? null : FOUNDATIONS.get(side);
    }

    /**
     * The one switch for failure behavior, read from Forge's production flag: true in a development
     * run, false in a release. In development a failure in module code a Satchel path runs (a
     * resolver, a module event's constructor, a consumer) propagates and crashes, and a SatchelHealth
     * violation throws; in release the failure is logged with the module id and the work continues.
     */
    public static boolean isDevelopment() {
        return !FMLEnvironment.production;
    }

    /**
     * Whether a foundation is installed for {@code side}, whichever thread asks.
     *
     * @deprecated Discouraged, and probably going to be removed: it puts Forge's {@code LogicalSide} on Satchel's
     *             API and invites code on one side to look at the other's. A thread asks which side it is on with
     *             {@link Satchel#isClient()} and {@link Satchel#isServer()}.
     */
    @Deprecated
    public static boolean isInstalled(LogicalSide side) {
        return FOUNDATIONS.containsKey(side);
    }

    /**
     * The foundation installed for {@code side}, whichever thread asks, or empty if none is.
     *
     * @deprecated Discouraged, and probably going to be removed: it puts Forge's {@code LogicalSide} on Satchel's
     *             API, and code that reaches the other side's foundation breaks the per-side split. Module code
     *             uses {@link Satchel#foundation()}, the calling thread's own, and {@link Satchel#isClient()}
     *             and {@link Satchel#isServer()} to ask which side that is.
     */
    @Deprecated
    public static Optional<LogicalFoundation> foundationFor(LogicalSide side) {
        return Optional.ofNullable(FOUNDATIONS.get(side));
    }

    /**
     * The mob jig {@code key} names, on the side the calling thread is bound to: the instance whose
     * interest API ({@code interest}, {@code scopeFor}, {@code lostInterest}, ...) a module calls.
     * There is no side-free entry point, so a call introduces a mob into the one jig it is made on.
     *
     * @throws SatchelException.JigNotFound if the jig is not installed on this side
     */
    public static MobJig mobJig(JigKey<MobJig> key) {
        Objects.requireNonNull(key, "key");
        return (MobJig) require().requireJigInfo(key).jig;
    }

    /**
     * The general readiness gate for the current thread's side -- false if no foundation is
     * installed yet at all, or if one is installed but {@link LogicalFoundation#isReady()} says
     * it's not safe to build on yet (see that method's docs for what "ready" means per side).
     * Callers that might run before readiness (rendering, commands, anything outside Satchel's
     * own ingress) should check this proactively and skip gracefully rather than let a
     * {@code SatchelException.NotReady} surface as their first signal.
     */
    public static boolean isReady() {
        return foundation().map(LogicalFoundation::isReady).orElse(false);
    }

    /**
     * Collapses the three-step "safe to touch yet" check a caller otherwise hand-writes before
     * every scope-info read -- {@link #isReady()}, then {@link LogicalFoundation#tryScopeInfo},
     * then {@link ScopeInfo#isReady()} -- into one call, one empty-vs-present result (SAT_077).
     * Every check here degrades to an empty {@code Optional}, never a throw -- the same "standby,
     * don't crash" contract each of the three steps already carries on its own.
     *
     * <p>
     * Deliberately a static method here, not an instance method on {@link LogicalFoundation}
     * reached via {@link #require()}: {@link #require()} throws if no foundation is installed for
     * this side at all, a strictly earlier and different condition than "installed but not ready"
     * -- checking {@link #isReady()} first, safely (it degrades through {@link #foundation()}'s
     * own {@code Optional}, never throwing), and only calling {@link #require()} once that's
     * already confirmed true, is what keeps this method exactly as crash-safe as the three-step
     * check it replaces, not a narrower version of it.
     *
     * <p>
     * Doesn't replace {@link AScopeCoupler}'s own {@code isReady(ScopeInfo)}/{@code
     * requireReady} -- those gate operations on a {@link ScopeInfo} the caller already has in
     * hand; this is the layer underneath that, for a caller that doesn't have one yet and needs to
     * find out whether it safely can.
     */
    public static Optional<ScopeInfo> tryReadyScopeInfo(JigKey<?> jigKey, SatchelScope scope) {
        if (!isReady()) return Optional.empty();
        return require().tryScopeInfo(jigKey, scope).filter(ScopeInfo::isReady);
    }

    /* =============================================================
     * Installation / activation
     * ========================================================== */

    public static void installFoundation(LogicalFoundation foundation) {
        Objects.requireNonNull(foundation, "foundation");

        LogicalSide side = foundation.side();

        if (FOUNDATIONS.putIfAbsent(side, foundation) != null) {
            throw new IllegalStateException(
                    "Satchel already installed for logical side: " + side
            );
        }
    }

    /**
     * Registers a jig as a side-keyed factory. Each side's booter calls the factories registered for
     * its side at its own foundation boot: a {@code SERVER} or {@code CLIENT} factory runs only on
     * that side, and a {@code BOTH} factory runs once per side and must return a fresh config each
     * time, so the two foundations share nothing. The registration owns the side: leave the config's
     * {@code sideApplicability} unset (a config that sets a different one crashes boot).
     *
     * <p>
     * Write the factory as a lambda that calls a static builder in a holder class
     * ({@code () -> TrackingJig.build()}), never a method reference: a method reference resolves its
     * class when the call site links, which would load a client-only config's classes on a dedicated
     * server. A registration made after its side has booted, or a second one for the same key and
     * side, fails.
     */
    public static void registerJig(JigPolicies.SideApplicability side, Supplier<? extends JigConfig<?, ?>> factory) {
        JigConfigCompiler.register(side, factory);
    }

    /**
     * Registers an ingress as a side-keyed factory, on the same terms as {@link #registerJig}: each
     * side's booter calls the factories for its side after its jigs and installs the config with its
     * own foundation. The ingress declares no side of its own.
     */
    public static void registerIngress(JigPolicies.SideApplicability side, Supplier<IngressConfig> factory) {
        IngressRegistry.register(side, factory);
    }

    /**
     * The adapter for a config already built at mod init: registers {@code () -> config} under the
     * config's own {@code sideApplicability}. A config that declares no side reads as {@code SERVER},
     * with a warning. Gives no lazy class loading, and a {@code BOTH} config registered this way is
     * one object shared by both sides' foundations; {@link #registerJig} gives each foundation a
     * fresh config.
     */
    public static void registerJigConfig(JigConfig<?, ?> config) {
        JigConfigCompiler.register(config);
    }

    /**
     * Contributes a Trinket to the <b>level host</b> -- a Satchel-owned jig with no bundles -- so a
     * module with no jig of its own gets per-level runtime behavior and a tick. For every ready level
     * scope on {@code side}, Satchel creates the Trinket and drops it at unload. Call at mod init: the
     * set freezes when the jig configs compile, and a later call throws.
     *
     * <p>
     * Look it up with {@code Satchel.trinket(HostJigs.LEVEL_HOST, levelScope, type)}. Existence says
     * only that the level scope is ready, so a Trinket that depends on a module's own bundles gates
     * itself with {@code Trinket#isReady}.
     *
     * <p>
     * <b>Write the factory as a lambda,</b> {@code Type.class, () -> new Type()}, never as a method reference
     * ({@code Type::new}). The class is named at mod init on every side, and a method reference links and
     * verifies it there: a Trinket whose body names a client-only type then stops a dedicated server from booting,
     * where the lambda links nothing until it runs. A Trinket that keeps client-only types out of its own body is
     * safe either way, but write the lambda regardless.
     *
     * @param moduleId the contributing module, shown by {@code /satchel trinkets}
     * @param side     the side or sides the Trinket exists on, so its code never checks the side
     * @param order    its place in the global tick; see {@code Order}
     */
    public static <T extends Trinket> void contributeLevelTrinket(
            String moduleId,
            JigPolicies.SideApplicability side,
            int order,
            Class<T> type,
            Supplier<? extends T> factory
    ) {
        HostJigs.contributeToLevels(moduleId, side, order, type, factory);
    }

    /**
     * Contributes a Trinket to the <b>player host</b>, per player scope. Otherwise as {@link
     * #contributeLevelTrinket}, including its rule that the factory is a lambda and never a method reference; look
     * it up with {@code HostJigs.PLAYER_HOST}.
     */
    public static <T extends Trinket> void contributePlayerTrinket(
            String moduleId,
            JigPolicies.SideApplicability side,
            int order,
            Class<T> type,
            Supplier<? extends T> factory
    ) {
        HostJigs.contributeToPlayers(moduleId, side, order, type, factory);
    }

    /* =============================================================
     * Semantic bundle access
     * ========================================================== */
    /**
     * Probe for a bundle without creating it.
     */
    public static <B extends SatchelBundle> Optional<B> ask(
            JigKey<?> jigKey,
            SatchelScope scope,
            BundleKey<B> bundleKey
    ) {
        Objects.requireNonNull(jigKey, "jigKey");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(bundleKey, "bundleKey");

        return foundation()                             // Optional<LogicalFoundation>
                .flatMap(f -> f.askJigInfo(jigKey))     // Optional<JigInfo>
                .map(info -> info.jig)                  // Optional<SatchelJig<?>>
                .flatMap(j -> j.ask(scope, bundleKey));
    }


    /**
     * The scope's {@link Trinket} of {@code type}, beside {@link #ask}. Empty while the scope isn't
     * ready or its Trinkets aren't attached yet -- "standby, don't crash". Uses the same side binding
     * as {@link #require()}: it works on the server thread and the client thread, which ingress
     * binds, and is empty on an unbound thread.
     *
     * @throws SatchelException.Generic if the jig doesn't declare {@code type} -- a programming error
     */
    public static <T extends Trinket> Optional<T> trinket(
            JigKey<?> jigKey,
            SatchelScope scope,
            Class<T> type
    ) {
        Objects.requireNonNull(jigKey, "jigKey");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(type, "type");

        Optional<JigInfo> jigInfo = foundation().flatMap(f -> f.askJigInfo(jigKey));
        if (jigInfo.isEmpty()) return Optional.empty();

        JigInfo ji = jigInfo.get();
        return ji.scopeInfo(scope).flatMap(info -> ji.coupler().trinket(info, type));
    }

    /**
     * Require an existing bundle.
     *
     * @throws IllegalStateException if missing or unavailable
     */
    public static <B extends SatchelBundle> B get(
            JigKey<?> jigKey,
            SatchelScope scope,
            BundleKey<B> bundleKey
    ) {
        Objects.requireNonNull(jigKey, "jigKey");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(bundleKey, "bundleKey");

        LogicalFoundation f = require();

        // 1. Resolve jig (authoritative)
        SatchelJig<?> jig = f.requireJigInfo(jigKey).jig;

        // 2. Enforce invariant explicitly
        JigKey.validateTypes(jigKey, jig);

        // 3. Require scope + readiness
        ScopeInfo info = f.requireScopeInfo(jigKey, scope);
        if (!info.isReady()) {
            throw new SatchelException.ScopeNotReady(scope.debugName());
        }

        // 4. Delegate (single, localized cast)
        @SuppressWarnings("unchecked")
        SatchelJig<SatchelScope> typed =
                (SatchelJig<SatchelScope>) jig;

        return typed.get(scope, bundleKey);
    }


    /**
     * Get or create a bundle, hydrating if necessary.
     *
     * @throws IllegalStateException on illegal scopeLifecycle or creation failure
     */
    public static <B extends SatchelBundle> B getOrCreate(
            JigKey<?> jigKey,
            SatchelScope scope,
            BundleKey<B> bundleKey
    ) {
        Objects.requireNonNull(jigKey, "jigKey");
        Objects.requireNonNull(scope, "scopeInfo");

        LogicalFoundation f = require();

        if (!f.requireScopeInfo(jigKey,scope).isReady()) {
            throw new SatchelException.ScopeNotReady(scope.debugName());
        }


        return f.requireJigInfo(jigKey).jig.getOrCreate(scope, bundleKey);
    }

    public static void requireServer() {
        var side = require().side();
        if (side != LogicalSide.SERVER) {
            throw new SatchelException.BadLogicalSide(
                    "This operation is server-only (current side: " + side + ")"
            ) ;
        }
    }
    public static void requireClient() {
        var side = require().side();
        if (side != LogicalSide.CLIENT) {
            throw new SatchelException.BadLogicalSide(
                    "This operation is client-only (current side: " + side + ")"
            ) ;
        }
    }

    /**
     * Whether the calling thread is the server's: the module-facing side check, so module code guards what
     * differs between the sides with a Satchel call and never with Forge's {@code LogicalSide} or a
     * level's {@code isClientSide()}. Exactly one of {@link #isServer()} and {@link #isClient()} is true.
     *
     * <p>
     * Reads the side through the thread binding, like {@link #require()}: it answers on a thread Satchel's
     * ingress bound (every tick, module event and Trinket call).
     *
     * @throws IllegalStateException on a thread no side is bound to, or if no foundation is installed for
     *         its side
     */
    public static boolean isServer() {
        var side = require().side();
        return side == LogicalSide.SERVER;
    }

    /**
     * Whether the calling thread is the client's: the module-facing side check, the counterpart of
     * {@link #isServer()} with the same thread binding and the same failures.
     *
     * @throws IllegalStateException on a thread no side is bound to, or if no foundation is installed for
     *         its side
     */
    public static boolean isClient() {
        var side = require().side();
        return side == LogicalSide.CLIENT;
    }
}
