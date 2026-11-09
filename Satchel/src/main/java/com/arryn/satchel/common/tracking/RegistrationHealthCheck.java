package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.IngressKey;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.LogicalSideContext;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * The registration slice of SatchelHealth (SAT_118) -- see {@link SatchelHealth}. Three empty
 * ingress registrations, one per side keyword, whose factories check the call they get:
 * <ul>
 *   <li>a {@code CLIENT} factory is never invoked on a dedicated server, so the classes it names
 *   never load there, and a {@code SERVER} one runs only for the server foundation;</li>
 *   <li>a {@code BOTH} factory is invoked once per side, on a bound thread, and each call returns its
 *   own config, so the two foundations share nothing;</li>
 *   <li>a factory runs at its own side's boot, after the thread is bound to that side.</li>
 * </ul>
 * Ingress is the vehicle because it is the smallest registration that needs no jig; jigs share the
 * mechanism ({@code JigConfigCompiler}), and SatchelHealth's host jigs register through it.
 */
final class RegistrationHealthCheck {

    private RegistrationHealthCheck() {}

    /** Which sides each factory has been invoked for. */
    private static final Map<String, EnumSet<LogicalSide>> INVOKED = new HashMap<>();

    static void register() {
        // Lambdas over a static builder, as every registration is written.
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER,
                () -> build("server-only", JigPolicies.SideApplicability.SERVER));
        Satchel.registerIngress(JigPolicies.SideApplicability.CLIENT,
                () -> build("client-only", JigPolicies.SideApplicability.CLIENT));
        Satchel.registerIngress(JigPolicies.SideApplicability.BOTH,
                () -> build("both", JigPolicies.SideApplicability.BOTH));
    }

    private static void violation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: Registration " + message);
    }

    private static synchronized IngressConfig build(String name, JigPolicies.SideApplicability registered) {
        LogicalSide side = LogicalSideContext.peek();
        if (side == null) {
            violation(name + " factory was invoked on a thread bound to no side");
            return new IngressConfig(new IngressKey("satchelhealth:registration_" + name));
        }

        if (registered == JigPolicies.SideApplicability.CLIENT
                && (side != LogicalSide.CLIENT || FMLEnvironment.dist.isDedicatedServer())) {
            violation("a CLIENT factory (" + name + ") was invoked for side " + side
                    + (FMLEnvironment.dist.isDedicatedServer() ? " on a dedicated server" : ""));
        }
        if (registered == JigPolicies.SideApplicability.SERVER && side != LogicalSide.SERVER) {
            violation("a SERVER factory (" + name + ") was invoked for side " + side);
        }

        if (!INVOKED.computeIfAbsent(name, k -> EnumSet.noneOf(LogicalSide.class)).add(side)) {
            violation("the " + name + " factory was invoked twice for side " + side);
        }

        IngressConfig config = new IngressConfig(new IngressKey("satchelhealth:registration_" + name));
        OUT.info("[SatchelHealth] Registration probe: the " + name + " factory was invoked for side "
                + side + " (config @" + Integer.toHexString(System.identityHashCode(config)) + ")");
        return config;
    }
}
