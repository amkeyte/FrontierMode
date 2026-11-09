package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.client.tracking.ClientLevelHostProbe;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.trinket.TrinketSet;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The host jigs' slice of SatchelHealth (SAT_121, SAT_131) -- see {@link SatchelHealth}. Satchel
 * contributes probe Trinkets to its own level host and player host, as any module would, and every
 * probe is a {@link HostProbe}, which confirms its own life (declared side only, attaches once, ticks
 * in both phases from the tick after it attached, detaches at unload). On top of that:
 * <ul>
 *   <li><b>per ready scope, on the declared side</b> -- a probe contributed for both sides is created
 *   for a level scope and a player scope on the server and on the client; one contributed for the
 *   server only exists on the server and not the client, for the level host and the player host
 *   alike; one contributed for the client only (the level host's) exists on the client and not the
 *   server, and on a dedicated server it is never constructed;</li>
 *   <li><b>a client-only contribution loads on a dedicated server</b> -- {@link ClientLevelHostProbe}
 *   is contributed for the client at mod init on every side with a lambda factory, and its tick body
 *   names a client-only type; a dedicated server that boots and runs confirms that nothing in the
 *   contribution path links or reflects on a contributed class. (A method reference to the constructor
 *   does link it, at mod init, and the server does not boot.)</li>
 *   <li><b>the contribution freeze</b> -- a contribution made once the jig configs have compiled is
 *   refused;</li>
 *   <li><b>the module-facing side check</b> -- {@code Satchel.isClient()} and {@code Satchel.isServer()}
 *   agree with the foundation's side, exactly one is true, and a thread no side is bound to is
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
        // A lambda, never a method reference: a method reference to the constructor resolves its target
        // when the call site links, which links (and so verifies) the class at mod init. On a dedicated
        // server that loads net.minecraft.client.Minecraft and the server does not boot.
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.CLIENT, Order.NORMAL,
                ClientLevelHostProbe.class, () -> new ClientLevelHostProbe());
        Satchel.contributePlayerTrinket(MODULE, JigPolicies.SideApplicability.BOTH, Order.NORMAL,
                PlayerHostProbe.class, PlayerHostProbe::new);
        Satchel.contributePlayerTrinket(MODULE, JigPolicies.SideApplicability.SERVER, Order.NORMAL,
                ServerOnlyPlayerProbe.class, ServerOnlyPlayerProbe::new);
    }

    /** The late-contribution check runs once, on the first level probe to attach. */
    private static final AtomicBoolean LATE_CHECKED = new AtomicBoolean();

    /** Whether the scope's coupler holds a Trinket of {@code type}. */
    private static boolean holds(ScopeInfo info, Class<?> type) {
        ScopeInfoImpl impl = ScopeInfoImpl.of(info);
        return impl.jigInfo().coupler().trinkets(impl).stream()
                .map(TrinketSet.Slot::type)
                .anyMatch(type::equals);
    }

    /**
     * On the level host, both sides. Also checks that the server-only probe is present on the server
     * and absent on the client, that the client-only probe is the reverse, and that a late
     * contribution is refused.
     */
    public static final class LevelHostProbe extends HostProbe {

        public LevelHostProbe() {
            super("level probe", JigPolicies.SideApplicability.BOTH);
        }

        @Override
        public void onAttach(ScopeInfo info) {
            super.onAttach(info);

            LogicalSide side = ScopeInfoImpl.of(info).jigInfo().side();
            boolean serverOnlyHeld = holds(info, ServerOnlyLevelProbe.class);
            if (serverOnlyHeld != (side == LogicalSide.SERVER)) {
                violation("server-only level probe is " + (serverOnlyHeld ? "held" : "missing") + " on the "
                        + side + " side: " + info.debugName());
            }
            boolean clientOnlyHeld = holds(info, ClientLevelHostProbe.class);
            if (clientOnlyHeld != (side == LogicalSide.CLIENT)) {
                violation("client-only level probe is " + (clientOnlyHeld ? "held" : "missing") + " on the "
                        + side + " side: " + info.debugName());
            }

            if (side == LogicalSide.SERVER && FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
                SatchelHealth.logOnce("host:client-contribution", "[SatchelHealth] Host contribution probe: on a "
                        + "dedicated server a Trinket contributed for the client only, whose body names a "
                        + "client-only type, was named at mod init and never constructed; the server booted and runs");
            }

            if (SatchelHealth.firstTime("host:side-guards:" + side)) {
                checkSideGuards(info, side);
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
     * The module-facing side check, from a bound thread and from one that is not: {@code Satchel.isClient()}
     * and {@code Satchel.isServer()} agree with this foundation's side and with each other (exactly one is
     * true), and a thread no side is bound to is refused and not answered.
     */
    private static void checkSideGuards(ScopeInfo info, LogicalSide side) {
        boolean client = Satchel.isClient();
        boolean server = Satchel.isServer();
        if (client != (side == LogicalSide.CLIENT) || server != (side == LogicalSide.SERVER) || client == server) {
            sideGuardViolation("Satchel.isClient()=" + client + " and Satchel.isServer()=" + server + " on the " + side
                    + " side: " + info.debugName());
        }

        // A new thread carries no binding, so both calls must throw there.
        boolean[] refused = new boolean[2];
        Thread unbound = new Thread(() -> {
            try {
                Satchel.isClient();
            } catch (IllegalStateException expected) {
                refused[0] = true;
            }
            try {
                Satchel.isServer();
            } catch (IllegalStateException expected) {
                refused[1] = true;
            }
        }, "satchel-health-unbound");
        unbound.start();
        try {
            unbound.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sideGuardViolation("the unbound-thread side-guard check was interrupted on the " + side + " side");
        }
        if (!refused[0] || !refused[1]) {
            sideGuardViolation("a thread with no side bound was answered by Satchel.isClient() ("
                    + (refused[0] ? "refused" : "answered") + ") or Satchel.isServer() ("
                    + (refused[1] ? "refused" : "answered") + ") instead of refused");
        }

        OUT.info("[SatchelHealth] Side-guard probe: Satchel.isClient() and isServer() agreed with the foundation's side "
                + "and with each other on the " + side + " side, and a thread with no side bound was refused by both");
    }

    private static void sideGuardViolation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: Host side guard " + message);
    }

    /**
     * On the level host, server only: held by every server level scope and no client one.
     */
    public static final class ServerOnlyLevelProbe extends HostProbe {

        public ServerOnlyLevelProbe() {
            super("server-only level probe", JigPolicies.SideApplicability.SERVER);
        }
    }

    /**
     * On the player host, both sides. Also checks that the server-only player probe is present on the
     * server and absent on the client.
     */
    public static final class PlayerHostProbe extends HostProbe {

        public PlayerHostProbe() {
            super("player probe", JigPolicies.SideApplicability.BOTH);
        }

        @Override
        public void onAttach(ScopeInfo info) {
            super.onAttach(info);

            LogicalSide side = ScopeInfoImpl.of(info).jigInfo().side();
            boolean serverOnlyHeld = holds(info, ServerOnlyPlayerProbe.class);
            if (serverOnlyHeld != (side == LogicalSide.SERVER)) {
                violation("server-only player probe is " + (serverOnlyHeld ? "held" : "missing") + " on the "
                        + side + " side: " + info.debugName());
            }
        }
    }

    /**
     * On the player host, server only: the shape of FRO_254's compass refresh and FRO_256's soulbound
     * stash.
     */
    public static final class ServerOnlyPlayerProbe extends HostProbe {

        public ServerOnlyPlayerProbe() {
            super("server-only player probe", JigPolicies.SideApplicability.SERVER);
        }
    }
}
