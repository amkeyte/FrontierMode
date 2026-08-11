package com.arryn.satchel;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.guts.*;
import com.arryn.satchel.common.newconfig.newnew.JigConfig;
import com.arryn.satchel.common.newconfig.newnew.JigConfigCompiler;
import net.minecraftforge.fml.LogicalSide;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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

    public static boolean isInstalled(LogicalSide side) {
        return FOUNDATIONS.containsKey(side);
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

//    public static void activateRegistrations(LogicalSide side) {
//        LogicalFoundation foundation = FOUNDATIONS.get(side);
//        if (foundation == null) {
//            throw new IllegalStateException(
//                    "Cannot activate registrations: Satchel not installed for " + side
//            );
//        }
//
//        SatchelJigRegistrar2.activateForSide(side, foundation);
//        SatchelStrapRegistrar.activateForSide(side,foundation);
//    }

    public static void registerJigConfig(JigConfig<?, ?> config) {
        JigConfigCompiler.register(config);
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

        return foundation()                         // Optional<LogicalFoundation>
                .flatMap(f -> f.askJig(jigKey))     // Optional<SatchelJig<?>>
                .flatMap(j -> j.ask(scope, bundleKey));
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
        SatchelJig<?> jig = f.requireJig(jigKey);

        // 2. Enforce invariant explicitly
        JigKey.validateTypes(jig, scope);

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


        return f.requireJig(jigKey).getOrCreate(scope, bundleKey);
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
        if (side != LogicalSide.SERVER) {
            throw new SatchelException.BadLogicalSide(
                    "This operation is client-only (current side: " + side + ")"
            ) ;
        }
    }

}
