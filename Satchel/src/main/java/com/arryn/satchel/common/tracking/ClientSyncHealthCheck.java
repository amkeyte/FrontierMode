package com.arryn.satchel.common.tracking;

import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;
import com.arryn.satchel.common.net.ParcelInbox;
import com.arryn.satchel.common.net.S2cBundleParcel;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fml.LogicalSide;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Whether the data a client is owed has arrived and been applied (SAT_126) -- see {@link SatchelHealth}.
 * The server syncs the canary's bundle to every client with a level scope (a parcel: the bundle's full
 * state in one message), and its fixture is ready on the client only once that parcel has been applied.
 * Satchel's own packet handlers and the client engine log a failure and drop the parcel, so a parcel
 * that never arrives or fails to apply leaves nothing but a fixture that never becomes ready. This probe
 * is where that is noticed, from outside, with nothing in Satchel's own code reporting to it: a client
 * level scope whose canary fixture is not ready this many ticks after the scope began ticking reports a
 * violation, once per scope. The server pushes a level's bundles to a player as they join it (a login, a
 * dimension change, a respawn), so the bound is a few ticks and not the timer's 100.
 *
 * <p>
 * It assumes SatchelHealth runs on both sides of the connection. A dropped world-identity token cannot
 * be seen here: without the token the client has no level scope, so no Trinket to tick.
 *
 * <p>
 * The same probe confirms the client's parcel inbox does not outlive its world (SAT_146). At a world's first
 * client level tick it finds nothing under a fixed stray id and plants one parcel there that no engine owns, so
 * the logout that ends the world has to empty the inbox for the next world's check to pass. A violation therefore
 * needs two worlds in one session.
 */
final class ClientSyncHealthCheck {

    private ClientSyncHealthCheck() {}

    /**
     * Ticks a client level scope waits for its canary parcel before calling it late: 3 seconds. Under the
     * timed resend (100 ticks), so a return to timer-only delivery fails the bound, and the arrival push
     * (a login, a dimension change, a respawn) meets it. Not tighter, because a portal entry reads 25 to 30
     * ticks by construction: vanilla sends the client its new level first, then spends a second or so
     * finding or building the portal and loading chunks before the server fires the dimension-change event
     * the push follows, and the scope's clock starts when the client has its level.
     */
    static final int PARCEL_WITHIN_TICKS = 60;

    /** The parcel the inbox check plants: ids fixed, so the next world finds it with no memory of this one. */
    private static final UUID STRAY_SCOPE = UUID.nameUUIDFromBytes(
            "satchelhealth:stray-parcel-scope".getBytes(StandardCharsets.UTF_8));
    private static final UUID STRAY_BUNDLE = UUID.nameUUIDFromBytes(
            "satchelhealth:stray-parcel-bundle".getBytes(StandardCharsets.UTF_8));

    /**
     * The inbox check, on the client at a world's first level tick: a parcel under the stray ids is the last
     * world's, since the logout has to have removed the one planted then. Then it plants the next one.
     */
    private static void checkInbox() {
        if (!ParcelInbox.drainForScope(STRAY_SCOPE, Set.of(STRAY_BUNDLE)).isEmpty()) {
            SatchelHealth.violation("[SatchelHealth] VIOLATION: Parcel inbox a parcel planted in the last world was "
                    + "still in the client's inbox at this world's start, so the logout did not empty it");
        }
        ParcelInbox.enqueue(new S2cBundleParcel(STRAY_SCOPE, STRAY_BUNDLE, "satchelhealth_stray", new CompoundTag()));
        OUT.info("[SatchelHealth] Parcel inbox probe: no parcel from an earlier world was in the client's inbox at "
                + "this world's start; one is planted for the next world to find gone");
    }

    /**
     * On the health level jig, which has a scope on both sides; acts on the client only. Declared on the
     * jig that holds the canary bundle, since a host Trinket has no bundles to ask.
     */
    public static final class SyncProbe implements Ticking {

        private int waited;
        private boolean done;

        @Override
        public void onTickAfter(ScopeInfoImpl info) {
            if (done || info.jigInfo().side() != LogicalSide.CLIENT) return;

            // Once per world, at its first client level tick.
            if (SatchelHealth.firstTime("parcel-inbox")) checkInbox();

            Optional<CanaryHealthCheck.CanaryFixture> fixture = info.ask(CanaryHealthCheck.CANARY_BUNDLE)
                    .flatMap(b -> b.get(CanaryHealthCheck.CANARY_FIXTURE));
            if (fixture.isPresent() && fixture.get().isReady()) {
                done = true;
                // One line per client level scope, so a dimension change and a respawn into another
                // dimension are each seen.
                OUT.info("[SatchelHealth] Client sync probe: the canary fixture's parcel arrived and was applied "
                        + waited + " tick(s) after the client's level scope for " + info.level().dimension().location()
                        + " began ticking");
                return;
            }

            if (++waited >= PARCEL_WITHIN_TICKS) {
                done = true;
                SatchelHealth.violation("[SatchelHealth] VIOLATION: Client sync the canary fixture was still not ready "
                        + PARCEL_WITHIN_TICKS + " ticks after " + info.debugName() + " began ticking, so the server's "
                        + "arrival push did not reach it: the parcel was not sent, was dropped before the inbox, or "
                        + "failed to apply (the packet handlers and the client engine log a failure and carry on); "
                        + "the timed resend, 100 ticks, would have covered it");
            }
        }

        @Override
        public String describe() {
            return "waited=" + waited + (done ? " done" : "");
        }
    }
}
