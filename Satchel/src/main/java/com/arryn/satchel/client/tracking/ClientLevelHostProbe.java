package com.arryn.satchel.client.tracking;

import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;
import com.arryn.satchel.common.tracking.HostProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.util.thread.BlockableEventLoop;

/**
 * SAT_131: a Trinket contributed to the level host for the client only, whose tick body names a
 * client-only type. {@code Satchel.contributeLevelTrinket} takes the class and factory at mod init on
 * every side, unlike {@code registerJig} and {@code registerIngress}, which take a lazy factory, so
 * this class is named on a dedicated server too and never constructed there. A dedicated server that
 * boots and runs without a {@code NoClassDefFoundError} confirms the contribution path neither links
 * nor reflects on a contributed class.
 *
 * <p>
 * The body is stricter than a module should write (FRO_256 keeps client types out of its Trinket and
 * delegates): the {@code Minecraft} instance is passed as its superclass, which the verifier can only
 * accept by loading {@code Minecraft}, so linking this class on a dedicated server would fail. That is
 * why its contribution is a lambda and not {@code ClientLevelHostProbe::new}: the method reference
 * resolves the constructor when the call site links, which links this class at mod init.
 */
public final class ClientLevelHostProbe extends HostProbe {

    public ClientLevelHostProbe() {
        super("client-only level probe", JigPolicies.SideApplicability.CLIENT);
    }

    @Override
    public void onTickAfter(ScopeInfoImpl info) {
        super.onTickAfter(info);

        if (!isOnItsOwnThread(Minecraft.getInstance())) {
            violation("client-only level probe ticked off the client thread on " + info.debugName());
        }
    }

    private static boolean isOnItsOwnThread(BlockableEventLoop<?> loop) {
        return loop != null && loop.isSameThread();
    }
}
