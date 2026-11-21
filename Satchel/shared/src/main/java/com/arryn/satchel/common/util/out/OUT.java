package com.arryn.satchel.common.util.out;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public final class OUT {

    public static final Logger LOGGER = LogUtils.getLogger();

    public static final OUT INSTANCE = new OUT();

    // ---------------------------------------------
    // TRACE state
    // ---------------------------------------------

    private final Tracer tracer = new Tracer("Satchel::OUT Logger");
    private final Map<String, Tracer> tracers = new HashMap<>();

    private OUT() {
        // IMPORTANT:
        // Do NOT touch INSTANCE here
        tracers.put(tracer.tracerId, tracer);
    }

    // ---------------------------------------------
    // TRACE accessors
    // ---------------------------------------------

    public static Tracer TRACE(String tracerId) {
        return INSTANCE.tracers.get(tracerId);
    }

    public static Tracer TRACE() {
        return INSTANCE.tracer;
    }

    public static void registerTracer(Tracer tracer) {
        INSTANCE.tracers.putIfAbsent(tracer.tracerId, tracer);
    }

    // ---------------------------------------------
    // Logging helpers (safe)
    // ---------------------------------------------

    public static void info(String msg) {
         LOGGER.info(msg);
    }

    public static void warn(String msg) {
         LOGGER.warn(msg);
    }

    public static void debug(String msg) {
         LOGGER.debug(msg);
    }

    public static void error(String msg, Throwable t) {
         LOGGER.error(msg, t);
    }

    public static void error(String msg) {
         LOGGER.error(msg);
    }


    // ---------------------------------------------
    // Utility
    // ---------------------------------------------

    public static String dumpStack() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        new Throwable().printStackTrace(pw);
        return sw.toString();
    }
}
