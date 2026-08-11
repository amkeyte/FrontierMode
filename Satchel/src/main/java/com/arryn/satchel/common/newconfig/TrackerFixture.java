package com.arryn.satchel.common.newconfig;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.fixture.SatchelFixture;

public final class TrackerFixture extends SatchelFixture {

    private long loads;
    private long unloads;
    private long externalTicks;
    private long internalTicks;


    public TrackerFixture(){}

    public void countLoaded() {
        loads++;
    }

    public void countUnloaded() {
        unloads++;
    }

    public void countExternalTick() {
        externalTicks++;
    }

    @Override
    public void onJigTick() {
        internalTicks++;
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
