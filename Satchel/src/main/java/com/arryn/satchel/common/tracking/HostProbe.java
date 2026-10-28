package com.arryn.satchel.common.tracking;

import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * What every SatchelHealth host probe checks about its own life (SAT_121, SAT_131, SAT_134): a Trinket
 * contributed to a host jig, as any module would, that confirms, loudly (a failure is logged as a
 * violation and the run goes on):
 * <ul>
 *   <li><b>it exists on its declared side only</b> -- a probe contributed for one side is never
 *   constructed on the other, and a CLIENT one is never constructed on a dedicated server;</li>
 *   <li><b>it attaches exactly once</b>, to a scope whose level is on the probe's own side;</li>
 *   <li><b>it ticks</b> -- both phases, strictly alternating, first in the tick after it attached,
 *   and a lookup through the coupler finds this very instance, and the scope's {@code jigKey()} is
 *   the key its jig is installed under;</li>
 *   <li><b>it detaches once</b>, after an attach, and is never ticked after.</li>
 * </ul>
 * Public only because Satchel constructs the subclasses by type, and so a probe that must name a
 * client-only type can live in the client package.
 */
public abstract class HostProbe implements Ticking {

    private final String name;
    private final JigPolicies.SideApplicability declared;
    private int attaches;
    private int detaches;
    private long before;
    private long after;
    private long attachedInTick = -1;
    private LogicalSide side;

    /**
     * @param name     what the probe is called in a violation message
     * @param declared the side or sides its contribution names
     */
    protected HostProbe(String name, JigPolicies.SideApplicability declared) {
        this.name = name;
        this.declared = declared;

        if (declared == JigPolicies.SideApplicability.CLIENT && FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            violation(name + " was contributed for the CLIENT only but was constructed on a dedicated server");
        }
    }

    /** Reports a broken invariant of this probe. */
    protected final void violation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: Host " + message);
    }

    public final String probeName() {
        return name;
    }

    public final int attaches() {
        return attaches;
    }

    public final int detaches() {
        return detaches;
    }

    /** The side of the foundation this probe attached under; null before its attach. */
    public final LogicalSide side() {
        return side;
    }

    /** The tick number this probe attached in; -1 before its attach. It first runs in the next tick. */
    public final long attachedInTick() {
        return attachedInTick;
    }

    /** How many {@code onTickAfter} calls this probe has had. */
    public final long ticksAfter() {
        return after;
    }

    @Override
    public void onAttach(ScopeInfo info) {
        ScopeInfoImpl impl = ScopeInfoImpl.of(info);
        attaches++;
        if (attaches > 1) violation(name + " attached " + attaches + " times to " + info.debugName());

        side = impl.jigInfo().side();
        if ((declared == JigPolicies.SideApplicability.SERVER && side != LogicalSide.SERVER)
                || (declared == JigPolicies.SideApplicability.CLIENT && side != LogicalSide.CLIENT)) {
            violation(name + " was contributed for " + declared + " but attached on the " + side + " side: "
                    + info.debugName());
        }

        boolean clientLevel = info.level().isClientSide();
        if (clientLevel != (side == LogicalSide.CLIENT)) {
            violation(name + " was created for side " + side + " but its scope's level is "
                    + (clientLevel ? "a client" : "a server") + " level: " + info.debugName());
        }
        attachedInTick = impl.jigInfo().trinketTick().tickNumber();
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
        ScopeInfoImpl impl = ScopeInfoImpl.of(info);
        checkLive(info, "onTickAfter");
        if (after + 1 != before) violation(name + " got onTickAfter without a matching onTickBefore on "
                + info.debugName());
        after++;

        // A lookup through the coupler finds this very instance once the scope is ready.
        if (impl.jigInfo().coupler().trinket(impl, getClass()).orElse(null) != this) {
            violation(name + " lookup did not return the attached probe on " + info.debugName());
        }

        // The key a module reads off its scope is the key the jig is installed under (SAT_132).
        if (!info.jigKey().equals(impl.jigInfo().key)) {
            violation(name + " scope's jigKey() is " + info.jigKey() + " but its jig is installed as "
                    + impl.jigInfo().key + " on " + info.debugName());
        }

        if (after == 1) {
            SatchelHealth.logOnce("host:" + name + ":" + side, "[SatchelHealth] Host probe " + name + " ("
                    + declared + ") attached once on the " + side + " side and ticked in both phases, with a "
                    + "jigKey() equal to its jig's, on " + info.debugName());
        }
    }

    private void checkLive(ScopeInfo info, String call) {
        if (attaches != 1 || detaches != 0) {
            violation(name + " got " + call + " while not attached on " + info.debugName());
        }
        if (ScopeInfoImpl.of(info).jigInfo().trinketTick().tickNumber() <= attachedInTick) {
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
