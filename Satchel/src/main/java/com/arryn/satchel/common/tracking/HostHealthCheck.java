package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.trinket.TrinketSet;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.fml.LogicalSide;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The host jigs' slice of SatchelHealth (SAT_121) -- see {@link SatchelHealth}. Satchel contributes
 * probe Trinkets to its own level host and player host, as any module would, and the probes confirm,
 * loudly (they throw, like every SatchelHealth check):
 * <ul>
 *   <li><b>per ready scope, on the declared side</b> -- a probe contributed for both sides is created
 *   for a level scope and a player scope on the server and on the client, attaches exactly once, and
 *   knows its own side; one contributed for the server only exists on the server and not the
 *   client;</li>
 *   <li><b>it ticks</b> -- both phases, strictly alternating, first in the tick after it attached;</li>
 *   <li><b>dropped at unload</b> -- detached after an attach, and never ticked after;</li>
 *   <li><b>the contribution freeze</b> -- a contribution made once the jig configs have compiled is
 *   refused.</li>
 * </ul>
 * The probes show in {@code /satchel trinkets} with the module id they were contributed under.
 */
final class HostHealthCheck {

    private HostHealthCheck() {}

    private static final String MODULE = "satchel_health";

    static void register() {
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.BOTH, Order.NORMAL,
                LevelHostProbe.class, LevelHostProbe::new);
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.SERVER, Order.NORMAL,
                ServerOnlyLevelProbe.class, ServerOnlyLevelProbe::new);
        Satchel.contributePlayerTrinket(MODULE, JigPolicies.SideApplicability.BOTH, Order.NORMAL,
                PlayerHostProbe.class, PlayerHostProbe::new);
    }

    private static void violation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: Host " + message);
    }

    /** The late-contribution check runs once, on the first level probe to attach. */
    private static final AtomicBoolean LATE_CHECKED = new AtomicBoolean();

    /**
     * What every host probe checks about its own life. Public only because Satchel constructs the
     * subclasses by type.
     */
    public abstract static class HostProbe implements Ticking {

        private final String name;
        private int attaches;
        private int detaches;
        private long before;
        private long after;
        private long attachedInTick = -1;
        private LogicalSide side;

        HostProbe(String name) {
            this.name = name;
        }

        @Override
        public void onAttach(ScopeInfo info) {
            attaches++;
            if (attaches > 1) violation(name + " attached " + attaches + " times to " + info.debugName());

            side = info.jigInfo().side();
            boolean clientLevel = info.level().isClientSide();
            if (clientLevel != (side == LogicalSide.CLIENT)) {
                violation(name + " was created for side " + side + " but its scope's level is "
                        + (clientLevel ? "a client" : "a server") + " level: " + info.debugName());
            }
            attachedInTick = info.jigInfo().trinketTick().tickNumber();
        }

        @Override
        public void onTickBefore(ScopeInfo info) {
            checkLive(info, "onTickBefore");
            if (before != after) violation(name + " got onTickBefore twice without an onTickAfter on "
                    + info.debugName());
            before++;
        }

        @Override
        public void onTickAfter(ScopeInfo info) {
            checkLive(info, "onTickAfter");
            if (after + 1 != before) violation(name + " got onTickAfter without a matching onTickBefore on "
                    + info.debugName());
            after++;

            // A lookup through the coupler finds this very instance once the scope is ready.
            if (info.jigInfo().coupler().trinket(info, getClass()).orElse(null) != this) {
                violation(name + " lookup did not return the attached probe on " + info.debugName());
            }
        }

        private void checkLive(ScopeInfo info, String call) {
            if (attaches != 1 || detaches != 0) {
                violation(name + " got " + call + " while not attached on " + info.debugName());
            }
            if (info.jigInfo().trinketTick().tickNumber() <= attachedInTick) {
                violation(name + " got " + call + " in the tick it was attached in on " + info.debugName());
            }
        }

        @Override
        public void onDetach(ScopeInfo info) {
            if (attaches != 1 || detaches != 0) {
                violation(name + " detached " + (attaches == 0 ? "without an attach" : "twice")
                        + " on " + info.debugName());
            }
            detaches++;
            OUT.info("[SatchelHealth] Host probe " + name + " (" + side + ") detached from "
                    + info.debugName() + " after " + after + " tick(s)");
        }

        @Override
        public String describe() {
            return side + " tickBefore=" + before + " tickAfter=" + after;
        }
    }

    /**
     * On the level host, both sides. Also checks that the server-only probe is present on the server
     * and absent on the client, and that a late contribution is refused.
     */
    public static final class LevelHostProbe extends HostProbe {

        public LevelHostProbe() {
            super("level probe");
        }

        @Override
        public void onAttach(ScopeInfo info) {
            super.onAttach(info);

            boolean server = info.jigInfo().side() == LogicalSide.SERVER;
            boolean serverOnlyHeld = info.jigInfo().coupler().trinkets(info).stream()
                    .map(TrinketSet.Slot::type)
                    .anyMatch(ServerOnlyLevelProbe.class::equals);
            if (serverOnlyHeld != server) {
                violation("server-only level probe is " + (serverOnlyHeld ? "held" : "missing") + " on the "
                        + info.jigInfo().side() + " side: " + info.debugName());
            }

            if (LATE_CHECKED.compareAndSet(false, true)) {
                boolean refused = false;
                try {
                    Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.BOTH, Order.NORMAL,
                            LevelHostProbe.class, LevelHostProbe::new);
                } catch (IllegalStateException expected) {
                    refused = true;
                }
                if (!refused) violation("a Trinket contributed after the jig configs compiled was accepted");
                OUT.info("[SatchelHealth] Host contribution freeze confirmed: a late contribution was refused");
            }
        }
    }

    /**
     * On the level host, server only: held by every server level scope and no client one.
     */
    public static final class ServerOnlyLevelProbe extends HostProbe {

        public ServerOnlyLevelProbe() {
            super("server-only level probe");
        }

        @Override
        public void onAttach(ScopeInfo info) {
            super.onAttach(info);
            if (info.jigInfo().side() != LogicalSide.SERVER) {
                violation("server-only level probe attached on the " + info.jigInfo().side() + " side");
            }
        }
    }

    /**
     * On the player host, both sides.
     */
    public static final class PlayerHostProbe extends HostProbe {

        public PlayerHostProbe() {
            super("player probe");
        }
    }
}
