package com.arryn.satchel.common.jig.config;

import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.guts.SatchelJig;
import com.arryn.satchel.common.jig.guts.ScopeCoupler;
import com.arryn.satchel.common.jig.guts.ScopeEngine;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.fml.LogicalSide;

import java.util.*;
import java.util.function.Supplier;

/**
 * Collects jig registrations during mod initialization and compiles them into fully materialized
 * {@link CompiledJigConfig} instances for a specific logical side.
 *
 * <p>
 * A registration is a side ({@code SERVER}, {@code CLIENT} or {@code BOTH}) and a factory. Each
 * side's booter calls {@link #compileForSide} at its own foundation boot, which runs the factories
 * registered for that side, so a {@code CLIENT} factory is never invoked on a dedicated server (the
 * classes it names never load there) and a {@code BOTH} factory is invoked once per side, giving
 * each foundation a fresh, independent config. The registration owns the side: the booter stamps it
 * onto the config, and a config that explicitly sets a different one crashes boot.
 *
 * <p>
 * Compilation is a one-way process:
 * <ul>
 *   <li>Validation is performed</li>
 *   <li>Jigs are instantiated</li>
 *   <li>Couplers are instantiated (no-arg)</li>
 *   <li>Couplers are configured via {@code installConfigs}</li>
 *   <li>Raw configuration never escapes this class</li>
 * </ul>
 *
 * <p>
 * Registration order is kept, and it is the order the foundation walks jigs.
 */
public final class JigConfigCompiler {

    private record Registration(
            JigPolicies.SideApplicability side,
            Supplier<? extends JigConfig<?, ?>> factory
    ) {}

    private static final List<Registration> REGISTRATIONS = new ArrayList<>();

    /** Sides whose factories have run: a registration for one of them is refused. */
    private static final Set<LogicalSide> BOOTED = EnumSet.noneOf(LogicalSide.class);

    private static boolean frozen = false;

    private JigConfigCompiler() {
    }

    /**
     * Whether compilation has begun for any side, after which a host contribution is refused.
     */
    public static boolean isFrozen() {
        return frozen;
    }

    /* =============================================================
     * Registration (mod init phase)
     * ========================================================== */

    /**
     * Registers a jig: {@code factory} is called by each foundation boot the registration applies
     * to, and must return a fresh config each time. Write the factory as a lambda that calls a static
     * builder in a holder class, not as a method reference, so a client-only config's classes load
     * only when its factory runs.
     *
     * @throws IllegalStateException if a side this applies to has already booted
     */
    public static synchronized void register(
            JigPolicies.SideApplicability side,
            Supplier<? extends JigConfig<?, ?>> factory
    ) {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(factory, "factory");

        for (LogicalSide logical : LogicalSide.values()) {
            if (appliesToSide(side, logical) && BOOTED.contains(logical)) {
                throw new IllegalStateException(
                        "Cannot register a jig for " + side + " after the " + logical
                                + " foundation has booted"
                );
            }
        }

        REGISTRATIONS.add(new Registration(side, factory));
    }

    /**
     * The adapter for a config built at mod init: registers {@code () -> config} under the config's
     * own {@code sideApplicability}. An unset one reads as {@code SERVER}, the old default, with a
     * deprecation warning. Gives no lazy class loading, and a {@code BOTH} config registered this
     * way is one object shared by both sides' foundations.
     */
    @SuppressWarnings("deprecation")
    public static void register(JigConfig<?, ?> config) {
        Objects.requireNonNull(config, "config");

        JigKey<?> key = Objects.requireNonNull(config.jigKey(), "config.jigKey");
        JigPolicies.SideApplicability side = config.binding().sideApplicability();
        if (side == null) {
            OUT.warn("[JigConfigCompiler] Jig " + key.name + " was registered with registerJigConfig and "
                    + "declares no side; reading it as SERVER. Register with Satchel.registerJig(side, "
                    + "factory) so the registration owns the side.");
            side = JigPolicies.SideApplicability.SERVER;
        }

        register(side, () -> config);
        OUT.debug("[JigConfigCompiler] register() key=" + key + " side=" + side);
    }

    /* =============================================================
     * Compilation (foundation boot phase)
     * ========================================================== */

    /**
     * Runs the factories registered for {@code side}, stamps the side on each config, validates it
     * and compiles it. Called once by that side's booter. Freezes registration for this side.
     */
    @SuppressWarnings("deprecation")
    public static synchronized List<CompiledJigConfig> compileForSide(LogicalSide side) {
        Objects.requireNonNull(side, "side");

        frozen = true;
        BOOTED.add(side);

        List<CompiledJigConfig> result = new ArrayList<>();
        Set<JigKey<?>> seen = new HashSet<>();

        for (Registration registration : REGISTRATIONS) {
            if (!appliesToSide(registration.side(), side)) {
                continue;
            }

            JigConfig<?, ?> cfg = Objects.requireNonNull(
                    registration.factory().get(),
                    "A jig factory returned null"
            );
            JigKey<?> jigKey = Objects.requireNonNull(cfg.jigKey(), "config.jigKey");

            // The registration owns the side. Unset takes the registration's; an explicit different
            // value is a crash, in development and release alike.
            JigPolicies.SideApplicability declared = cfg.binding().sideApplicability();
            if (declared != null && declared != registration.side()) {
                throw new IllegalStateException(
                        "Jig " + jigKey.name + " declares sideApplicability " + declared
                                + " but is registered for " + registration.side()
                                + ". The registration owns the side; leave the config's unset."
                );
            }
            cfg.binding().sideApplicability(registration.side());

            if (!seen.add(jigKey)) {
                throw new IllegalStateException(
                        "Duplicate JigConfig for key " + jigKey + " on side " + side +
                                ". Exactly one JigConfig per JigKey is allowed."
                );
            }

            JigConfig.Presets<?, ?> p = cfg.presets();

            JigConfigValidator.validate(jigKey, p);

            result.add(new CompiledJigConfig(
                    jigKey,
                    cfg.binding(),
                    cfg.execution(),
                    cfg.policies(),
                    cfg.bundles(),
                    cfg.trinkets()
            ));
        }

        OUT.debug("[JigConfigCompiler] compileForSide() side=" + side + " compiled=" + result.size());
        return List.copyOf(result);
    }

    /* =============================================================
     * Instantiation helpers
     * ========================================================== */

    //find me a home later.
    public static SatchelJig<?> instantiateJig(
            JigKey<?> jigKey,
            CompiledJigConfig config
    ) {
        try {
            SatchelJig<?> jig =
                    config.binding().jigType().getDeclaredConstructor().newInstance();
            jig.installJigConfig(config);
            jig.installKey(jigKey);
            JigKey.validateTypes(jigKey, jig);
            return jig;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to instantiate jig for key " + jigKey +
                            " (" + (config.binding().jigType() == null
                            ? "<null>"
                            : config.binding().jigType().getName()) + ")",
                    e
            );
        }
    }

    //find me a home later.
    public static ScopeCoupler instantiateCoupler(
            JigKey<?> jigKey,
            CompiledJigConfig config,
            ScopeEngine engine
    ) {
        try {
            var coupler = config.binding().couplerType().getDeclaredConstructor().newInstance();
            coupler.installJigConfig(config);
            //var engine = Satchel.require().booter().engine();
            engine.installJigConfig(config);
            engine.registerBundleSchema(config.bundles().schema());
            engine.freezeBundleSchema();

            coupler.installEngine(engine);
            return  coupler;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to instantiate ScopeCoupler for jig " + jigKey +
                            " (" + (config.binding().couplerType() == null
                            ? "<null>"
                            : config.binding().couplerType().getName()) + ")",
                    e
            );
        }
    }

    /* =============================================================
     * Helpers
     * ========================================================== */

    private static boolean appliesToSide(
            JigPolicies.SideApplicability applicability,
            LogicalSide side
    ) {
        return switch (applicability) {
            case BOTH -> true;
            case CLIENT -> side == LogicalSide.CLIENT;
            case SERVER -> side == LogicalSide.SERVER;
        };
    }
}
