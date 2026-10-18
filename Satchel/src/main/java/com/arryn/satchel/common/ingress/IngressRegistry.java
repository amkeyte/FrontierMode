package com.arryn.satchel.common.ingress;

import com.arryn.satchel.common.identity.SatchelKey;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.fml.LogicalSide;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Ingress registrations, kept the way jig registrations are: a side ({@code SERVER}, {@code CLIENT}
 * or {@code BOTH}) and a factory, called by each side's booter at its own foundation boot, after its
 * jigs. A {@code CLIENT} factory is never invoked on a dedicated server, so the classes it names
 * never load there. A {@code BOTH} factory is invoked once per side and must return a fresh config
 * each time. Write the factory as a lambda that calls a static builder in a holder class, not as a
 * method reference.
 *
 * <p>
 * A second registration of the same ingress key on one side fails at that side's boot, and so does a
 * registration made after the side's factories have run.
 */
public final class IngressRegistry {

    private record Registration(
            JigPolicies.SideApplicability side,
            Supplier<IngressConfig> factory
    ) {}

    private static final List<Registration> REGISTRATIONS = new ArrayList<>();

    /** Sides whose factories have run: a registration for one of them is refused. */
    private static final Set<LogicalSide> BOOTED = EnumSet.noneOf(LogicalSide.class);

    private IngressRegistry() {
    }

    /**
     * @throws IllegalStateException if a side this applies to has already booted
     */
    public static synchronized void register(
            JigPolicies.SideApplicability side,
            Supplier<IngressConfig> factory
    ) {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(factory, "factory");

        for (LogicalSide logical : LogicalSide.values()) {
            if (appliesTo(side, logical) && BOOTED.contains(logical)) {
                throw new IllegalStateException(
                        "Cannot register an ingress for " + side + " after the " + logical
                                + " foundation has booted");
            }
        }

        REGISTRATIONS.add(new Registration(side, factory));
    }

    /**
     * Runs the factories registered for {@code foundation}'s side and installs each result with it.
     * Called once by that side's booter, after its jigs.
     */
    public static synchronized void installFor(LogicalFoundation foundation) {
        Objects.requireNonNull(foundation, "foundation");
        LogicalSide side = foundation.side();
        BOOTED.add(side);

        Set<SatchelKey<?>> seen = new HashSet<>();
        int installed = 0;

        for (Registration registration : REGISTRATIONS) {
            if (!appliesTo(registration.side(), side)) continue;

            IngressConfig config = Objects.requireNonNull(
                    registration.factory().get(), "An ingress factory returned null");
            if (!seen.add(config.key())) {
                throw new IllegalStateException(
                        "Duplicate ingress " + config.key().name + " on side " + side
                                + ". Exactly one registration per ingress key and side is allowed.");
            }

            config.install(foundation);
            installed++;
        }

        OUT.debug("[IngressRegistry] installed " + installed + " ingress config(s) for side " + side);
    }

    private static boolean appliesTo(JigPolicies.SideApplicability applicability, LogicalSide side) {
        return switch (applicability) {
            case BOTH -> true;
            case CLIENT -> side == LogicalSide.CLIENT;
            case SERVER -> side == LogicalSide.SERVER;
        };
    }
}
