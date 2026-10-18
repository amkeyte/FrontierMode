package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.identity.WorldIdentityContext;
import com.arryn.satchel.common.lifecycle.*;
import com.arryn.satchel.common.lifecycle.EventHandlers;
import com.arryn.satchel.common.jig.config.CompiledJigConfig;
import com.arryn.satchel.common.jig.config.JigConfigCompiler;
import com.arryn.satchel.common.trinket.TrinketDecl;
import com.arryn.satchel.common.trinket.TrinketTick;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.fml.LogicalSide;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class LogicalFoundation {

    /* =============================================================
     * Identity
     * ========================================================== */

    private final LogicalSide side;

    public LogicalFoundation(LogicalSide side) {
        this.side = Objects.requireNonNull(side, "side");
    }

    public LogicalSide side() {
        return side;
    }

    /**
     * Whether this foundation is safe to build on yet -- the general readiness gate any
     * Satchel-dependent code (present or future) should check before doing anything, rather than
     * each feature reinventing its own "am I too early" logic.
     *
     * <p>
     * Server: trivially true once this instance exists. {@code ServerForgeIngress} binds the
     * world-identity token synchronously, before introducing any source, in the same handler that
     * is this foundation's first real opportunity to do anything -- there's no observable window
     * where a server foundation exists but isn't fully ready.
     *
     * <p>
     * Client: additionally requires the world-identity token (RM_SAT_019) to have been received
     * and bound. Before that, a client-side {@code LevelScope}'s identity isn't stable yet --
     * see {@code LevelScope.determineUUID}, which throws {@code SatchelException.NotReady} rather
     * than compute a value once this is the enforced precondition, instead of the earlier design's
     * silent dimension-only fallback.
     */
    public boolean isReady() {
        if (side == LogicalSide.CLIENT) {
            return WorldIdentityContext.current().isPresent();
        }
        return true;
    }

    /* =============================================================
     * Compiled configuration (installed once, pre-use)
     * ========================================================== */
    public void installConfigs(List<CompiledJigConfig> configs) {
        Objects.requireNonNull(configs, "configs");


        for (CompiledJigConfig cfg : configs) {
            var key = cfg.jigKey();
            var jig = JigConfigCompiler.instantiateJig(key,cfg);
            var coupler = JigConfigCompiler.instantiateCoupler(
                    key,
                    cfg,
                    booter().engine());

            var info = new JigInfo(
                    key, jig, coupler, orderedJigInfos.size(), scopeSequence::getAndIncrement, trinketTick,
                    side);
            info.installJigConfig(cfg);
            if (jigInfos.putIfAbsent(key, info) == null) {
                orderedJigInfos.add(info);
            }

            EventHandlers handlers = cfg.execution().eventHandlers();
            if (handlers != null) {
                handlers.install(eventBus());
            }

            checkExecutionPulseHealth(key, cfg);
        }

        OUT.debug("Installed " + configs.size() +
                " jig configs for side " + side);
        logJigOrder();
        logTrinketOrder();
    }

    /**
     * Prints the order the declared Trinkets will tick in, once per foundation after its configs
     * install: by {@code order}, then jig registration index, then declaration index. A scope's
     * creation sequence, the next key, exists only once scopes do; it orders the same Trinket across
     * scopes of one jig.
     */
    private void logTrinketOrder() {
        record Declared(TrinketTick.Key key, JigKey<?> jig, String type, String owner) {}

        List<Declared> declared = new ArrayList<>();
        for (JigInfo ji : orderedJigInfos) {
            int declIndex = 0;
            for (TrinketDecl<?> decl : ji.trinketDecls()) {
                if (!decl.appliesTo(side)) continue;
                declared.add(new Declared(
                        new TrinketTick.Key(decl.order(), ji.registrationIndex(), 0L, declIndex++, 0L),
                        ji.key,
                        decl.type().getSimpleName(),
                        decl.owner()));
            }
        }
        if (declared.isEmpty()) return;

        declared.sort(Comparator.comparing(Declared::key));
        StringBuilder sb = new StringBuilder("Trinket order for side ").append(side).append(':');
        for (Declared d : declared) {
            sb.append("\n  order ").append(d.key().order())
                    .append(" | jig ").append(d.key().jigIndex()).append(' ').append(d.jig())
                    .append(" | ").append(d.type());
            if (d.owner() != null) sb.append(" | from ").append(d.owner());
        }
        OUT.info(sb.toString());
    }

    /**
     * Prints the order the pulse walks jigs in, once per foundation after its configs install. The
     * walk is registration order, so the same mod set prints the same list on every run.
     */
    private void logJigOrder() {
        StringBuilder sb = new StringBuilder("Jig order for side ").append(side).append(':');
        for (JigInfo ji : orderedJigInfos) {
            sb.append("\n  ").append(ji.registrationIndex()).append(". ").append(ji.key);
        }
        OUT.info(sb.toString());
    }

    /**
     * RM_SAT_013: boot-time half of the silent-inertness health-check. Catches the FRO_018 shape
     * at the source instead of waiting for a human to notice gameplay isn't syncing or
     * persisting: a jig that allows the persistence or networking capability but never enables
     * execution-pulse participation ticks fine (if tick is on) but never flushes or syncs
     * anything -- {@code ScopeEngine_Server.flushIfDirty()}/{@code scheduleSync()} and
     * {@code ScopeEngine_Client.applyIncomingParcels()} only ever run from inside
     * {@code onExecutionPulse} (see the Jig & Scope Runtime wiki page). No error, no exception --
     * just quiet inertness. This only checks the two static config categories against each other
     * at boot; it can't detect the separate, still-open question of whether the very first parcel
     * push is proactive or purely interval-based -- see RM_SAT_013's own roadmap node log for
     * that finding.
     */
    private void checkExecutionPulseHealth(JigKey<?> key, CompiledJigConfig cfg) {
        var caps = cfg.policies().capabilities();
        boolean allowsFlushOrSync = caps.allowsPersistence() || caps.allowsNetworking();
        boolean executionPulseOn = cfg.execution().lifecycle().participatesInExecutionPulse();

        if (allowsFlushOrSync && !executionPulseOn) {
            OUT.warn(
                    "[health-check] Jig " + key + " allows persistence and/or networking but "
                            + "does not participate in the execution pulse -- it will tick (if "
                            + "tick is enabled) but never flush or sync anything. Same silent-"
                            + "inertness shape as FRO_018. Fix: call "
                            + ".execution().lifecycle(...withExecutionPulse(true)) in this jig's "
                            + "JigConfig."
            );
        }
    }

    /* =============================================================
     * Jig registry (runtime state)
     * ========================================================== */


    /** Single source of truth for all jig-owned runtime state, for lookup by key */
    private final Map<JigKey<?>, JigInfo> jigInfos =
            new ConcurrentHashMap<>();

    /**
     * The same {@link JigInfo}s in registration order. Appended to only while configs install, so
     * the pulse and the commands iterate it without a map's arbitrary order.
     */
    private final List<JigInfo> orderedJigInfos = new CopyOnWriteArrayList<>();

    /** Per-foundation counter behind {@link ScopeInfo#creationSequence()}. */
    private final AtomicLong scopeSequence = new AtomicLong();

    /** The global, sorted list of every ready scope's ticking Trinkets. */
    private final TrinketTick trinketTick = new TrinketTick();

    public TrinketTick trinketTick() {
        return trinketTick;
    }

    public Collection<JigKey<?>> registeredJigs() {
        return orderedJigInfos.stream().<JigKey<?>>map(ji -> ji.key).toList();
    }

    /**
     * Every installed jig, in registration order.
     */
    public List<JigInfo> jigInfos() {
        return Collections.unmodifiableList(orderedJigInfos);
    }

    public Optional<JigInfo> askJigInfo(JigKey<?> key) {
        return Optional.ofNullable(jigInfos.get(key));
    }

    public JigInfo requireJigInfo(JigKey<?> key) {
        Objects.requireNonNull(key, "key");

        JigInfo info = jigInfos.get(key);
        if (info != null) {
            return info;
        }

        throw new SatchelException.JigNotFound(
                "JigInfo not installed for key: " + key
        );
    }

    public void introduceSource(Object source) {
        Objects.requireNonNull(source, "source");

        boolean matched = false;

        // Registration order, so one source's scopes get creation sequences in the same order on
        // every run.
        for (JigInfo ji : orderedJigInfos) {

            Optional<SatchelScope> opt = ji.resolveScope(source);
            if (opt.isEmpty()) continue;

            matched = true;

            SatchelScope scope = opt.get();

            // SAT_042 gated this log to first-time introduction only (see prior comment history)
            // -- fixed the repeat-poll spam for an already-tracked mob, but not the underlying
            // volume: this guard's mutation (ji.addScope below) still runs once per genuinely new
            // scope of every entity Satchel sees at all -- every ambient bat, every chicken, not
            // just boss mobs -- and with them constantly spawning and despawning, "once per new
            // scope" is itself a permanent stream. Confirmed routine now, same "expected, remove
            // rather than downgrade" call as AScopeCoupler's BundleNotFound and ScopeInfo's own
            // CREATED/PHASE lines -- the mutation itself (the actual behavior) is untouched.
            if (!ji.hasScope(scope)) {
                ji.addScope(scope, source);
            }
        }

        if (!matched) {
            OUT.debug("Scope introduction from source matched no jigs.");
        }
    }
    public void tryRemoveSource(Object source) {
        Objects.requireNonNull(source, "source");

        for (JigInfo ji : orderedJigInfos) {

            Optional<SatchelScope> opt = ji.resolveScope(source);
            if (opt.isEmpty()) continue;

            ScopeInfo info = ji.scopeInfo(opt.get()).orElse(null);
            if (info == null) continue;

            ji.jig.onUnload(info);
        }
    }
    /* =============================================================
     * Scope access helpers
     * ========================================================== */

    public ScopeInfo requireScopeInfo(JigKey<?> jigKey, SatchelScope scope) {
        JigInfo ji = requireJigInfo(jigKey);

        JigKey.validateTypes(jigKey, ji.jig);

        return ji.scopeInfo(scope).orElseThrow(
                () -> new SatchelException.ScopeNotFound(
                        "Jig " + jigKey + " does not know scope " + scope.debugName()
                )
        );
    }

    /**
     * Non-throwing sibling of {@link #requireScopeInfo(JigKey, SatchelScope)}. "This jig doesn't
     * know this scope yet" is a legitimate, expected state now that client-side scope recognition
     * can be deferred (see {@code LevelResolver.resolveScope}'s RM_SAT_019 block/defer behavior) —
     * a caller reached during that window (e.g. render code running before the world-identity
     * token round-trip completes) should treat it the same as any other "not ready yet" state
     * rather than crash. Type mismatches (wrong jig for this scope type) still throw via
     * {@code JigKey.validateTypes} -- that's a real programming error, not a timing window.
     */
    public Optional<ScopeInfo> tryScopeInfo(JigKey<?> jigKey, SatchelScope scope) {
        // Uses askJigInfo (non-throwing) rather than requireJigInfo -- this method's own doc
        // promises to be the "non-throwing sibling" of requireScopeInfo, but delegating the
        // jig-lookup itself to requireJigInfo broke that promise for one real case: a jig whose
        // config genuinely isn't installed on this LogicalFoundation yet (as opposed to a jig
        // that's installed but doesn't know this particular scope yet, which the code below this
        // already handled correctly). Client-side, that window is real -- Satchel.isReady() can
        // flip true (world-identity token bound) a moment before a BOTH-applicability jig's
        // config finishes landing on the freshly-created client foundation, and a caller reached
        // in that exact window (e.g. render code) used to crash with JigNotFound instead of
        // getting the "not ready yet, standby" empty Optional this method exists to provide.
        Optional<JigInfo> jiOpt = askJigInfo(jigKey);
        if (jiOpt.isEmpty()) {
            return Optional.empty();
        }
        JigInfo ji = jiOpt.get();

        JigKey.validateTypes(jigKey, ji.jig);

        return ji.scopeInfo(scope);
    }

    /* =============================================================
     * Lifecycle infrastructure (installed during glue phase)
     * ========================================================== */

    private SatchelEventBus eventBus;
    private ScopeLifecycleDispatcher scopeLifecycle;
    private FoundationLifecycleDispatcher foundationLifecycle;
    private BundleLifecycleDispatcher bundleLifecycle;

    public void installEventBus() {
        if (eventBus == null) {
            eventBus = new SatchelEventBus();
            OUT.debug("EventBus installed");
        }
    }

    public SatchelEventBus eventBus() {
        requireInstalled(eventBus, "EventBus");
        return eventBus;
    }

    public void installScopeLifecycle() {
        if (scopeLifecycle == null) {
            scopeLifecycle = new ScopeLifecycleDispatcher(this);
            OUT.debug("ScopeLifecycle installed");
        }
    }

    public ScopeLifecycleDispatcher scopeLifecycle() {
        requireInstalled(scopeLifecycle, "ScopeLifecycleDispatcher");
        return scopeLifecycle;
    }

    public void installFoundationLifecycle() {
        if (foundationLifecycle == null) {
            foundationLifecycle = new FoundationLifecycleDispatcher(this);
            OUT.debug("FoundationLifecycle installed");
        }
    }

    public FoundationLifecycleDispatcher foundationLifecycle() {
        requireInstalled(foundationLifecycle, "FoundationLifecycleDispatcher");
        return foundationLifecycle;
    }

    public void installBundleLifecycle() {
        if (bundleLifecycle == null) {
            bundleLifecycle = new BundleLifecycleDispatcher(this);
            OUT.debug("BundleLifecycle installed");
        }
    }

    public BundleLifecycleDispatcher bundleLifecycle() {
        requireInstalled(bundleLifecycle, "BundleLifecycleDispatcher");
        return bundleLifecycle;
    }

    private static void requireInstalled(Object o, String name) {
        if (o == null) {
            throw new IllegalStateException(name + " not installed");
        }
    }

    /* =============================================================
     * Booter
     * ========================================================== */
    private ASatchelFoundationBooter booter;
    public void installBooter(ASatchelFoundationBooter booter) {
        this.booter = Objects.requireNonNull(booter, "booter");
        OUT.debug("Foundation booter installed for side " + side);
    }
    private ASatchelFoundationBooter booter() {
        if (booter == null) {
            throw new IllegalStateException(
                    "ASatchelFoundationBooter not installed for side: " + side
            );
        }
        return booter;
    }

    /* =============================================================
     * Forge egress (RM_SAT_022, "Roger")
     * ========================================================== */
    private ForgeEgress egress;

    /**
     * Installed once per foundation from inside each concrete booter's own
     * {@code installFoundation()} -- {@code ServerFoundationBooter} installs {@code
     * ServerForgeEgress}, {@code ClientFoundationBooter} installs {@code ClientForgeEgress}.
     * Deliberately not routed through {@link #booter()} -- {@code booter()} is private (SAT_038),
     * and a side-agnostic caller like {@code MobJig} should never need it just to resolve a UUID.
     */
    public void installEgress(ForgeEgress egress) {
        this.egress = Objects.requireNonNull(egress, "egress");
        OUT.debug("ForgeEgress installed for side " + side);
    }

    public ForgeEgress egress() {
        requireInstalled(egress, "ForgeEgress");
        return egress;
    }

    /* =============================================================
     * Diagnostics
     * ========================================================== */

    @Override
    public String toString() {
        return "LogicalFoundation[" + side + "]";
    }
}
