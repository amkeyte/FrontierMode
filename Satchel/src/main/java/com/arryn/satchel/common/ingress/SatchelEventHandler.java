package com.arryn.satchel.common.ingress;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.guts.LogicalFoundation;
import com.arryn.satchel.common.util.out.OUT;

import java.util.Objects;

/**
 * Base for a handler of one native (Forge) event. Satchel registers every handler with Forge, so a
 * module never writes {@code @SubscribeEvent} or calls {@code addListener} and cannot skip the
 * guards.
 *
 * <p>
 * The final {@link #onEvent} applies the first guard, then hands the event to the module's {@link
 * #handle}:
 * <ul>
 *   <li><b>Foundation comparison.</b> Forge's bus is shared, so a listener one side's ingress
 *   registered is also called for the other side's thread. A firing proceeds only if the foundation
 *   bound to the calling thread is this handler's own foundation and is ready. Anything else is
 *   dropped silently: the other side's thread, a thread Satchel has not bound, a foundation not yet
 *   up. The comparison names no side, so no module code branches on one.</li>
 * </ul>
 * The resolver bounce and the no-subscriber check belong to the handlers {@link
 * IngressConfig#translate} builds; a module writes a handler of its own only for one that does more
 * than translate.
 *
 * <p>
 * <b>Failure.</b> One switch, {@link Satchel#isDevelopment()}: in development a throw from {@code
 * handle} propagates and crashes; in release it is logged with the ingress's name and the firing is
 * dropped.
 *
 * <p>
 * <b>Stateless.</b> A handler lives as long as the JVM while an integrated server starts and stops
 * many times inside it, so it keeps no per-world state. State belongs in a Trinket or a fixture; the
 * handler finds it through the event.
 *
 * <p>
 * A handler instance serves one ingress on one side: a {@code BOTH} registration's factory builds a
 * fresh handler per side.
 *
 * @param <N> the native event type, unconstrained here; the registration layer binds it to Forge's
 *            {@code Event}
 */
public abstract class SatchelEventHandler<N> {

    private final Class<N> nativeType;
    private LogicalFoundation foundation;
    private String owner;

    protected SatchelEventHandler(Class<N> nativeType) {
        this.nativeType = Objects.requireNonNull(nativeType, "nativeType");
    }

    /**
     * The native event this handler listens for.
     */
    public final Class<N> nativeType() {
        return nativeType;
    }

    /**
     * Ties the handler to its ingress's foundation. Once; called when the ingress installs.
     */
    final void bind(LogicalFoundation foundation, String owner) {
        if (this.foundation != null) {
            throw new IllegalStateException(
                    "A handler serves one ingress on one side; " + getClass().getName()
                            + " is already bound. Build a fresh handler in the factory.");
        }
        this.foundation = Objects.requireNonNull(foundation, "foundation");
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    /**
     * What Forge calls. Applies the guards, then {@link #handle}.
     */
    public final void onEvent(N event) {
        LogicalFoundation own = foundation;
        if (own == null) return;
        if (Satchel.peekFoundation() != own || !own.isReady()) return;

        try {
            handle(event);
        } catch (RuntimeException e) {
            if (Satchel.isDevelopment()) throw e;
            OUT.error("[Ingress " + owner + "] a handler for " + nativeType.getSimpleName()
                    + " failed; dropping the firing", e);
        }
    }

    /**
     * The module's handling of one firing that passed the guards.
     */
    protected abstract void handle(N event);

    /**
     * Posts a module event to this ingress's foundation's bus and closes it when the last consumer
     * returns, after which a read of its native event throws.
     */
    protected final void post(ModuleSignal<?, ?> signal) {
        Objects.requireNonNull(signal, "signal");
        try {
            foundation.eventBus().post(signal);
        } finally {
            signal.close();
        }
    }

    /**
     * The foundation's event bus, for the no-subscriber check.
     */
    final com.arryn.satchel.common.lifecycle.SatchelEventBus bus() {
        return foundation.eventBus();
    }
}
