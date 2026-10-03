package com.arryn.satchel.common.jig.config;

import com.arryn.satchel.common.identity.JigKey;

import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.trinket.TrinketDecl;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class JigConfigValidator {

    private JigConfigValidator() {}

    public static void validate(
            JigKey<?> key,
            JigConfig.Presets<?, ?> p
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(p, "presets");

        validateBinding(key, p);
        validateBundles(key, p);
        validateExecution(key, p);
        validatePolicies(key, p);
        validateCapabilities(key, p);
        validateTrinkets(key, p);
    }

    // =============================================================
    // Binding
    // =============================================================

    private static void validateBinding(
            JigKey<?> key,
            JigConfig.Presets<?, ?> p
    ) {
        if (p.binding == null) {
            throw new IllegalStateException(
                    "Jig " + key + " missing binding configuration"
            );
        }

        if (p.binding.jigType == null) {
            throw new IllegalStateException(
                    "Jig " + key + " missing jigType"
            );
        }

        if (p.binding.scopeType == null) {
            throw new IllegalStateException(
                    "Jig " + key + " missing scopeType"
            );
        }

        if (p.binding.sourceType == null) {
            throw new IllegalStateException(
                    "Jig " + key + " missing sourceType"
            );
        }

        if (p.binding.couplerClass == null) {
            throw new IllegalStateException(
                    "Jig " + key + " missing couplerClass"
            );
        }

        if (p.binding.scopeResolver == null) {
            throw new IllegalStateException(
                    "Jig " + key + " missing scopeResolver"
            );
        }

        if (p.binding.uuidDeterminer == null) {
            throw new IllegalStateException(
                    "Jig " + key + " missing uuidDeriver"
            );
        }

        if (p.binding.sideApplicability == null) {
            throw new IllegalStateException(
                    "Jig " + key + " missing sideApplicability"
            );
        }
    }

    // =============================================================
    // Bundles
    // =============================================================

    private static void validateBundles(
            JigKey<?> key,
            JigConfig.Presets<?, ?> p
    ) {
        if (p.bundles == null || p.bundles.schema == null) {
            throw new IllegalStateException(
                    "Jig " + key + " missing bundle schema"
            );
        }
    }

    // =============================================================
    // Execution
    // =============================================================

    private static void validateExecution(
            JigKey<?> key,
            JigConfig.Presets<?, ?> p
    ) {
        if (p.execution == null) {
            throw new IllegalStateException(
                    "Jig " + key + " missing execution configuration"
            );
        }

        if (p.execution.lifecycle == null) {
            throw new IllegalStateException(
                    "Jig " + key + " missing lifecycle policy"
            );
        }

        if (p.execution.executionPriority == null) {
            throw new IllegalStateException(
                    "Jig " + key + " missing executionPriority"
            );
        }

        if (p.execution.tickOrder == null) {
            throw new IllegalStateException(
                    "Jig " + key + " missing tickOrder"
            );
        }
    }

    // =============================================================
    // Policies
    // =============================================================

    private static void validatePolicies(
            JigKey<?> key,
            JigConfig.Presets<?, ?> p
    ) {
        if (p.policies == null) {
            throw new IllegalStateException(
                    "Jig " + key + " missing policies configuration"
            );
        }

        if (p.policies.readiness == null) {
            throw new IllegalStateException(
                    "Jig " + key + " must declare readiness policy"
            );
        }

        if (p.policies.sync == null) {
            throw new IllegalStateException(
                    "Jig " + key + " must declare sync policy"
            );
        }

        if (p.policies.persistence == null) {
            throw new IllegalStateException(
                    "Jig " + key + " must declare persistence policy"
            );
        }

        if (p.policies.errors == null) {
            throw new IllegalStateException(
                    "Jig " + key + " must declare error policy"
            );
        }

        if (p.policies.diagnostics == null) {
            throw new IllegalStateException(
                    "Jig " + key + " must declare diagnostics policy"
            );
        }

        if (p.policies.capabilities == null) {
            throw new IllegalStateException(
                    "Jig " + key + " must declare capabilities"
            );
        }
    }

    // =============================================================
    // Capability Cross-Validation
    // =============================================================

    private static void validateCapabilities(
            JigKey<?> key,
            JigConfig.Presets<?, ?> p
    ) {
        JigPolicies.Capabilities caps = p.policies.capabilities;
        JigPolicies.Lifecycle lifecycle = p.execution.lifecycle;

        // ---- Clock requirement ---------------------------------

        if (caps.allowsClock()
                && !lifecycle.participatesInTick()
                && !lifecycle.participatesInExecutionPulse()) {

            throw new IllegalStateException(
                    "Jig " + key +
                            " requires clock but lifecycle does not participate in tick or execution pulse"
            );
        }

        // ---- Persistence requirement ----------------------------

        if (caps.allowsPersistence()) {

            if (!p.policies.persistence.persistent()) {
                throw new IllegalStateException(
                        "Jig " + key +
                                " requires persistence but persistence policy is not persistent"
                );
            }

            // Catches at boot what would otherwise only surface as a live AccessFailed crash
            // the moment a bundle actually claiming Capability.PERSISTENCE gets created --
            // ScopeEngine_Server.resolveServerLevel() throws if ScopeInfo.referenceLevel() is
            // null, and that only happens if binding().referenceLevel(...) was never called.
            // Confirmed the hard way (a real server crash, not a hypothetical) before this
            // check existed -- see SAT_060/SAT_063's logs.
            if (p.binding.referenceLevel == null) {
                throw new IllegalStateException(
                        "Jig " + key +
                                " allows persistence but declares no referenceLevel resolver -- " +
                                "any bundle that actually claims Capability.PERSISTENCE will " +
                                "throw AccessFailed the moment it's created, not now. Set one " +
                                "via .binding().referenceLevel(...)."
                );
            }

//            if (p.policies.ephemeralScopes) { //TODO compile hack
//                throw new IllegalStateException(
//                        "Jig " + key +
//                                " requires persistence but declares ephemeral scopes"
//                );
//            }
        }

        // ---- Networking requirement -----------------------------

        if (caps.allowsNetworking()) {

//            if (!p.policies.sync.networkingEnabled()) { //TODO compile hack
//                throw new IllegalStateException(
//                        "Jig " + key +
//                                " requires networking but sync policy does not enable networking"
//                );
//            }

            if (p.binding.sideApplicability
                    != JigPolicies.SideApplicability.BOTH) {

                throw new IllegalStateException(
                        "Jig " + key +
                                " requires networking but is restricted to a single logical side"
                );
            }
        }
    }

    // =============================================================
    // Trinkets
    // =============================================================

    private static void validateTrinkets(
            JigKey<?> key,
            JigConfig.Presets<?, ?> p
    ) {
        Set<Class<?>> seen = new HashSet<>();

        for (TrinketDecl<?> decl : p.bundles.trinkets) {
            if (!seen.add(decl.type())) {
                throw new IllegalStateException(
                        "Jig " + key + " declares Trinket " + decl.type().getName() + " twice"
                );
            }

            if (Ticking.class.isAssignableFrom(decl.type())
                    && !p.execution.lifecycle.participatesInTick()) {
                throw new IllegalStateException(
                        "Jig " + key + " declares Ticking Trinket " + decl.type().getName()
                                + " but its lifecycle does not participate in tick -- set "
                                + "Lifecycle.withTick(true)"
                );
            }
        }
    }
}
