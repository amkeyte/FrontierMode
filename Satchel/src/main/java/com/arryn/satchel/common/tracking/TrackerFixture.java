package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.fixture.SatchelFixture;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public final class TrackerFixture extends SatchelFixture {

    private long loads;
    private long unloads;
    private long externalTicks;
    private long internalTicks;


    public TrackerFixture() {
        registerLong("loads", () -> loads, val -> loads = val);
        registerLong("unloads", () -> unloads, val -> unloads = val);
        registerLong("externalTicks", () -> externalTicks, val -> externalTicks = val);
        registerLong("internalTicks", () -> internalTicks, val -> internalTicks = val);
    }

    public void countLoaded() {
        loads++;
        markDirtyOnServer();
    }

    public void countUnloaded() {
        unloads++;
        markDirtyOnServer();
    }

    /**
     * Only the server's copy is saved and synced. A client bundle is a read-only mirror, and the client
     * engine warns when one is dirty, so the client keeps counting and marks nothing.
     */
    private void markDirtyOnServer() {
        if (Satchel.isServer()) markDirty();
    }

    public void countExternalTick() {
        externalTicks++;
        //does not mark dirty. catch it at unload.
    }

    @Override
    public void onJigTick() {
        internalTicks++;
        //does not mark dirty. catch it at unload.
    }

    @Override
    public String toString() {
        return "TrackerFixture{" +
                "loads=" + loads +
                ", unloads=" + unloads +
                ", externalTicks=" + externalTicks +
                ", internalTicks=" + internalTicks +
                '}';
    }
}
