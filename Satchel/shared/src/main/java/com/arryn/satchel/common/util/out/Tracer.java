package com.arryn.satchel.common.util.out;

import com.arryn.satchel.common.util.schedule.SchedulerClock;
import com.arryn.satchel.common.util.schedule.TickThrottler;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class Tracer {
    private static final Logger LOGGER = LogUtils.getLogger();
    public final String tracerId;
    private final Map<String, Caller> CALLERS = new ConcurrentHashMap<>();
    private boolean enabled = true;


    // =====================================================================
    // ctor
    // =====================================================================

    public Tracer() {
        this.tracerId = String.valueOf(System.identityHashCode(this));
        ;
    }

    public Tracer(String tracerId) {
        this.tracerId = tracerId;
    }


    // =====================================================================
    // properties
    // =====================================================================

    public boolean isEnabled() {
        return enabled;
    }

    public Tracer enable() {
        enabled = true;
        return this;
    }

    public Tracer disable() {
        enabled = false;
        return this;
    }


    // =====================================================================
    // usage
    // =====================================================================


    public void log(Object source, String message) {
        if (!isEnabled()) return;
        log(source, null, message);
    }

    /**
     * Logs the given message at a throttled rate. The first message is sent,
     * then log calls are ignored for this source/tag session until the
     * clock source interval has elapsed.
     * @param source the object sending the log call.
     * @param tag an identifier to track throttling. In the event that a
     *            source+tag combination a new throttling session will be
     *            created using default values suitable for tight loop logging.
     * @param message the message to display.
     */
    public void log(Object source, String tag, String message) {
        if (!isEnabled()) return;
        String sourceId = System.identityHashCode(source) + ":" + tag;
        Caller caller = getCaller(source, tag);
        traceHelper(caller, message);
    }

    /**
     * General purpose log call to LOGGER.trace(msg);
     * No throttling.
     * @param msg the message to write;
     */
    public void log(String msg) {
        if (isEnabled()) LOGGER.trace("[TRACE] {}", msg);
    }

    private void traceHelper(Caller caller, String message) {
        if (!isEnabled() || !caller.isEnabled()) {
            return;
        }

        // Apply throttling only if configured
        if (caller.throttler != null) {
            if (caller.throttler.disAllow()) {
                caller.gatedCalls++;
                return;
            }
        }

        // Emit log -- genuinely TRACE level (below the project's default 'debug' console
        // level in build.gradle), not LOGGER.debug: this class exists specifically for tight,
        // high-frequency diagnostic logging (a per-render-frame readiness check, a per-packet
        // parcel send/enqueue/drain) that's meant to be off during normal dev play and switched
        // on deliberately when someone's actually chasing something at this level of detail --
        // not to leak through by default just because the console happens to be at DEBUG.
        LOGGER.trace(
                "(@{} - {}) {}",
                caller.sourceId,
                caller.interval != null ? caller.interval : "unthrottled",
                message
        );
    }

    // =====================================================================
    // Register
    // =====================================================================


    public void registerCaller(
            Object source,
            SchedulerClock clockSource,
            Long interval) {

        Caller caller = new Caller(
                source,
                clockSource,
                interval
        );

        registerCaller(caller);
    }


    public void registerCaller(Object source, long interval) {
        Caller caller = new Caller(
                source,
                new SchedulerClock.AutoClock(),
                interval);

        registerCaller(caller);
    }

    public void registerCaller(
            Object source,
            String tag,
            SchedulerClock clockSource,
            Long interval) {

        Caller caller = new Caller(
                Caller.determineSourceId(source, tag),
                clockSource,
                interval);

        registerCaller(caller);
    }
    public void registerCaller(Object source, SchedulerClock clockSource,long interval, String... tags) {
        for (String tag : tags) {
            Caller caller = new Caller(
                    Caller.determineSourceId(source, tag),
                    clockSource,
                    interval
            );

            registerCaller(caller);
        }
    }

    public void registerCaller(
            Object source,
            String tag,
            Supplier<SchedulerClock> clockSource,
            Long interval) {

        Caller caller = new Caller(
                Caller.determineSourceId(source, tag),
                clockSource.get(),
                interval);

        registerCaller(caller);
    }


    public void registerCaller(Caller caller) {
        CALLERS.putIfAbsent(caller.sourceId, caller);
    }

    public void registerCallers(Iterable<Caller> callers) {
        for (Caller caller : callers) {
            registerCaller(caller);
        }
    }
    // =====================================================================
    // Registrar access
    // =====================================================================


    public Caller getCaller(Object source) {
        return getCaller(source, null);
    }


    public Caller getCaller(Object source, String tag) {
        String sourceId = Caller.determineSourceId(source,tag);


        return CALLERS.computeIfAbsent(
                sourceId,
                (sId) -> new Caller(
                        sId,
                        new SchedulerClock.AutoClock(),
                        200L
                )
        );
    }


    public boolean removeCaller(String callerId) {
        return CALLERS.remove(callerId) != null;
    }

    //call site examples
    private void sketch1() {

        var TRACE = new Tracer();

        //register a new caller
//        var caller1 = new Caller("source1", new ServerWorldTimeClockSource(), 500L); //TODO not client safe
//        TRACE.registerCaller(caller1);

        //register a new caller with null clock source that just
        TRACE.registerCaller(this, "sketch1", SchedulerClock.NullClock::new, 20L);

        //register a caller that traces every other add
        var callerSource1 = new ArrayList<>();
        var clock1 = new SchedulerClock.AutoClock();
        TRACE.registerCaller(callerSource1, clock1, 2L);

//        //register and create a new time based caller
//        TRACE.registerCaller(this, "boo", ServerWorldTimeClockSource::new, 100L);

        //register an automatic counter (call allow()) with a deterministic id.
        String callerSourceId1 = Caller.determineSourceId(this);
        TRACE.registerCaller(callerSourceId1, 200L);

        //register an automatic timer.
        TRACE.registerCaller(this, 10L);
    }


    public static class Caller {
        public final Object source;
        public final String sourceId;
        public final SchedulerClock clock;
        public final Long interval;
        public final TickThrottler throttler; // may be null
        long gatedCalls;
        private boolean enabled = true;

        public Caller(Object source, SchedulerClock clock, Long interval) {
            this.source = source;

            if (source == null) {
                sourceId = "NullSource_";
            } else if (source instanceof String s) {
                sourceId = s;
            } else {
                sourceId = String.valueOf(System.identityHashCode(source));
            }

            this.clock = clock;
            this.interval = interval;

            this.throttler = (interval != null && interval > 0)
                    ? new TickThrottler(interval, clock)
                    : null;
        }


        private static String addTag(String sourceId, String tag) {
            if (tag == null || tag.isBlank()) return sourceId;

            return sourceId + "::" + tag;
        }

        public static String determineSourceId(Object source, String tag) {
            if (source == null) {
                return addTag("NullSource", tag);
            } else if (source instanceof String s) {
                // Intentional, stable semantic ID
                return addTag(s, tag);
            } else {
                // JVM-identity grouping
                String sourceIdHash = String.valueOf(System.identityHashCode(source));
                return addTag(sourceIdHash, tag);
            }
        }

        public static String determineSourceId(Object source) {
            return determineSourceId(source, null);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public Caller enable() {
            enabled = true;
            return this;
        }

        public Caller disable() {
            enabled = false;
            return this;
        }

        public long gatedCalls() {
            return gatedCalls;
        }

        public long resetGatedCalls() {
            long currentCalls = gatedCalls;
            gatedCalls = 0;
            return currentCalls;
        }
    }
}
