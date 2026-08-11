package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.lifecycle.*;
import com.arryn.satchel.common.newconfig.newnew.CompiledJigConfig;
import com.arryn.satchel.common.newconfig.newnew.JigConfigCompiler;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.fml.LogicalSide;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

            var info = new JigInfo(key,jig,coupler);
            info.installJigConfig(cfg);
            jigInfos.putIfAbsent(key,info);
        }

        OUT.debug("Installed " + configs.size() +
                " jig configs for side " + side);
    }

    /* =============================================================
     * Jig registry (runtime state)
     * ========================================================== */


    /** Single source of truth for all jig-owned runtime state */
    private final Map<JigKey<?>, JigInfo> jigInfos =
            new ConcurrentHashMap<>();

    public Collection<JigKey<?>> registeredJigs() {
        return jigInfos.keySet();
    }

    public Collection<JigInfo> jigInfos() {
        return jigInfos.values();
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

        for (JigInfo ji : jigInfos.values()) {

            Optional<SatchelScope> opt = ji.resolveScope(source);
            if (opt.isEmpty()) continue;

            matched = true;

            SatchelScope scope = opt.get();

            OUT.debug(
                    "[JigLifecycle][introduceSource] "
                            + scope.debugName()
                            + " → "
                            + ji.key
            );

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

        for (JigInfo ji : jigInfos.values()) {

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

        JigKey.validateTypes(ji.jig, scope);

        return ji.scopeInfo(scope).orElseThrow(
                () -> new SatchelException.ScopeNotFound(
                        "Jig " + jigKey + " does not know scope " + scope.debugName()
                )
        );
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
    public ASatchelFoundationBooter booter() {
        if (booter == null) {
            throw new IllegalStateException(
                    "ASatchelFoundationBooter not installed for side: " + side
            );
        }
        return booter;
    }

    /* =============================================================
     * Diagnostics
     * ========================================================== */

    @Override
    public String toString() {
        return "LogicalFoundation[" + side + "]";
    }
}
