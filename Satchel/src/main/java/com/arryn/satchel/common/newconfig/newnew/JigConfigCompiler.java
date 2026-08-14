package com.arryn.satchel.common.newconfig.newnew;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.guts.SatchelJig;
import com.arryn.satchel.common.jig.guts.ScopeCoupler;
import com.arryn.satchel.common.jig.guts.ScopeEngine;
import net.minecraftforge.fml.LogicalSide;

import java.util.*;

/**
 * Collects {@link JigConfig} declarations during mod initialization
 * and compiles them into fully materialized {@link CompiledJigConfig}
 * instances for a specific logical side.
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
 */
public final class JigConfigCompiler {

    private static final Map<JigKey<?>, JigConfig<?, ?>> CONFIGS =
            new LinkedHashMap<>();

    private static boolean frozen = false;

    private JigConfigCompiler() {
    }

    /* =============================================================
     * Registration (mod init phase)
     * ========================================================== */

    public static void register(JigConfig<?, ?> config) {
        Objects.requireNonNull(config, "config");

        if (frozen) {
            throw new IllegalStateException(
                    "Cannot register JigConfig after compilation has begun"
            );
        }

        JigKey<?> key = Objects.requireNonNull(config.jigKey(), "config.jigKey");

        if (CONFIGS.containsKey(key)) {
            throw new IllegalStateException(
                    "Duplicate JigConfig for key " + key +
                            ". Exactly one JigConfig per JigKey is allowed."
            );
        }

        CONFIGS.put(key, config);
    }

    /* =============================================================
     * Compilation (foundation boot phase)
     * ========================================================== */

    public static List<CompiledJigConfig> compileForSide(LogicalSide side) {
        Objects.requireNonNull(side, "side");

        frozen = true;

        List<CompiledJigConfig> result = new ArrayList<>();

        for (var entry : CONFIGS.entrySet()) {

            JigKey<?> jigKey = entry.getKey();
            JigConfig<?, ?> cfg = entry.getValue();

            JigConfig.Presets<?, ?> p = cfg.presets();

            if (!appliesToSide(p.binding.sideApplicability, side)) {
                continue;
            }

            JigConfigValidator.validate(jigKey, p);

            CompiledJigConfig compiled =
                    new CompiledJigConfig(
                            jigKey,
                            cfg.binding(),
                            cfg.execution(),
                            cfg.policies(),
                            cfg.bundles()
                    );

            result.add(compiled);
        }

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
