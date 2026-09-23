package com.arryn.satchel.common.lifecycle;

import net.minecraftforge.eventbus.api.Event;

import java.util.Objects;

/**
 * Base shape for a bare, unscoped signal posted to {@link SatchelEventBus} that wraps a single
 * real Forge event — the reusable pattern {@link MobDied} was the first instance of, generalized
 * (SAT_072) to also cover signals that need real cancellation, not just one-way notification.
 *
 * <p>
 * Deliberately one uniform shape rather than a cancelable/non-cancelable class split: {@link
 * #cancel()} passes straight through to whatever the wrapped {@code forgeEvent} actually
 * supports. Satchel doesn't get to decide up front which signals "should" be vetoable — it just
 * reflects the real event's own truth. A signal wrapping a non-cancelable Forge event (or one
 * that's cancelable in principle but posted after the fact, with nothing left to veto — {@link
 * MobDied}'s own shape, if it's ever ported onto this base) throws from {@link #cancel()} rather
 * than silently no-opping, the same "loud failure, not a wrong answer" discipline {@code
 * LogicalSideContext.require()} already established elsewhere in Satchel.
 *
 * <p>
 * {@link #cancel()} mutates the wrapped {@code forgeEvent} directly — a signal always wraps the
 * exact live event instance the producing ingress class is currently handling, so there's no
 * separate flag to read back; the ingress method simply returns after {@code
 * SatchelEventBus.post()}, and Forge sees the cancellation already applied.
 *
 * <p>
 * {@link #allow()}/{@link #deny()} are the {@code @HasResult}-shaped sibling of {@link #cancel()}
 * (SAT_076) — Forge's other, independent veto mechanism (some events, e.g. {@code
 * MobSpawnEvent.SpawnPlacementCheck}, use {@code Event.Result} instead of the cancel flag; a
 * handful use both). Same pass-through discipline: reflects whatever {@code forgeEvent.hasResult()}
 * actually says, throws rather than silently no-opping if it's false.
 *
 * <p>
 * Producers: {@code ServerForgeIngress} and other sanctioned, concern-grouped ingress classes
 * (e.g. {@code ServerSpawnIngress}, SAT_076) per the Forge Integration & Sidedness Contract's
 * ingress discipline — see {@code ItemPickupSignal}/{@code BlockBreakSignal} for the first two
 * concrete signals built on this base.
 *
 * @param <E> the concrete Forge event type this signal wraps
 */
public abstract class SatchelSignal<E extends Event> {

    private final E forgeEvent;

    protected SatchelSignal(E forgeEvent) {
        this.forgeEvent = Objects.requireNonNull(forgeEvent, "forgeEvent");
    }

    /** The raw Forge event, for consumers that need details this signal doesn't surface directly. */
    public final E forgeEvent() {
        return forgeEvent;
    }

    /** Whether this signal has already been vetoed, by this or an earlier subscriber. */
    public final boolean isCanceled() {
        return forgeEvent.isCanceled();
    }

    /**
     * Vetoes the operation this signal represents, by canceling the wrapped Forge event.
     *
     * @throws UnsupportedOperationException if the wrapped Forge event isn't cancelable — a
     *                                        capability gap of this specific signal instance, not
     *                                        a state error, so this is the JDK exception whose
     *                                        documented meaning actually fits.
     */
    public final void cancel() {
        if (!forgeEvent.isCancelable()) {
            throw new UnsupportedOperationException(
                    "Signal " + getClass().getSimpleName() + " wraps a non-cancelable Forge event ("
                            + forgeEvent.getClass().getSimpleName() + ") -- nothing to veto."
            );
        }
        forgeEvent.setCanceled(true);
    }

    /**
     * Explicitly allows the operation this signal represents, via Forge's {@code Event.Result}
     * mechanism (independent of {@link #cancel()}'s cancel-flag mechanism).
     *
     * @throws UnsupportedOperationException if the wrapped Forge event has no result to set.
     */
    public final void allow() {
        requireHasResult();
        forgeEvent.setResult(Event.Result.ALLOW);
    }

    /**
     * Denies the operation this signal represents, via Forge's {@code Event.Result} mechanism
     * (independent of {@link #cancel()}'s cancel-flag mechanism).
     *
     * @throws UnsupportedOperationException if the wrapped Forge event has no result to set.
     */
    public final void deny() {
        requireHasResult();
        forgeEvent.setResult(Event.Result.DENY);
    }

    /** The current {@code Event.Result}, for a consumer that wants to read rather than set it. */
    public final Event.Result result() {
        requireHasResult();
        return forgeEvent.getResult();
    }

    private void requireHasResult() {
        if (!forgeEvent.hasResult()) {
            throw new UnsupportedOperationException(
                    "Signal " + getClass().getSimpleName() + " wraps a Forge event ("
                            + forgeEvent.getClass().getSimpleName() + ") with no Result -- nothing to set."
            );
        }
    }
}
