package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.GlobalConsumers;
import com.arryn.satchel.common.ingress.ModuleEvent;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Three real loader events a module gates a spawn or a conversion with, driven end to end (SAT_115) -- see
 * {@link SatchelHealth}. Each is a module event on a real native event, with the same shape a game module gives it: a
 * resolver that filters, a context (the level, the mob, the converting mob) and one consumer. Server only.
 * <ul>
 *   <li><b>The placement check</b>, a result event, through vanilla's own {@code SpawnPlacements.checkSpawnRules}, so
 *   the firing travels the path the game uses;</li>
 *   <li><b>the position check</b>, a result event, through the loader's own position check;</li>
 *   <li><b>the pre-conversion event</b>, cancelable, through the loader's own conversion check.</li>
 * </ul>
 * Each call returns a boolean, so what a consumer decided is visible in what the game would do. A probe calls it three
 * ways: <i>untouched</i> (the consumer only watches), and then with a <i>deny</i> and an <i>allow</i> for the two result
 * events, or a <i>cancel</i> for the conversion. Every call must build and deliver exactly one module event, an
 * untouched one must leave the game's own answer, a deny must stop, an allow must force through, and a cancel must stop.
 * A fourth call with no window open must build nothing, which is the cheap rejection a resolver gives a hot event.
 *
 * <p>
 * The <i>window</i> is what keeps live play out of it. A resolver answers only on the thread that opened the window,
 * and only while it is open, so a natural spawn or a conversion in play is never built into an event and never denied.
 * The calls happen on the server's own thread, from the level host's tick, once per world after the spawn area has
 * loaded. The conversion probe uses a throwaway mob that never joins the world. The window is this world's, in its
 * {@link HealthState}.
 *
 * <p>
 * The check is shared, and typed on {@code ServerLevelAccessor}, a common type. Each line's {@code SpawnGateIngress}
 * implements the three event interfaces on its loader's events, resolves them with the context hooks below, calls the
 * built hooks from its constructors, installs {@link SpawnGateHealthCheck#consumers()}, and registers the check with
 * its {@link Loader}, the loader's two calls the game reaches only through the loader.
 */
final class SpawnGateHealthCheck {

    private SpawnGateHealthCheck() {}

    private static final String MODULE = "satchel_health";

    /** Contributes the driver, which makes the loader's calls through {@code loader}. Called by each line. */
    static void register(Loader loader) {
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.SERVER, Order.LATE,
                GateDriver.class, () -> new GateDriver(loader));
    }

    private static void violation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: Spawn gate " + message);
    }

    /** The loader's own calls that fire the position check and the pre-conversion event. Implemented by each line. */
    interface Loader {

        /** Fires the loader's position check for {@code mob}, as the natural spawner would; whether it may spawn there. */
        boolean checkSpawnPosition(Mob mob, ServerLevelAccessor level);

        /** Fires the loader's pre-conversion event for {@code mob} becoming {@code outcome}; whether it may convert. */
        boolean canConvert(Mob mob, EntityType<? extends LivingEntity> outcome);
    }

    // =================================================================
    // The window
    // =================================================================

    /** What the consumers do to the event under test. */
    enum Mode { OBSERVE, DENY, ALLOW, CANCEL }

    /**
     * The probe's one open window and its counts, one per world. Everything is touched on the server thread, inside the
     * call the driver makes, except {@link Window#open()}, which a resolver asks on whatever thread posts the event.
     */
    static final class Window {
        private volatile Thread thread;
        private volatile Mode mode = Mode.OBSERVE;
        private final AtomicInteger built = new AtomicInteger();
        private final AtomicInteger delivered = new AtomicInteger();
        private volatile ModuleSignal.Verdict arrival;
        private volatile boolean gameAnswer;

        boolean open() {
            return thread == Thread.currentThread();
        }

        void begin(Mode m) {
            mode = m;
            built.set(0);
            delivered.set(0);
            arrival = null;
            thread = Thread.currentThread();
        }

        void end() {
            thread = null;
        }
    }

    private static Window window() {
        return SatchelHealth.state().spawnGate;
    }

    // =================================================================
    // The module events, and what a line's adapter calls
    // =================================================================

    /** A gate whose native event carries a result. */
    interface Decided {
        ModuleSignal.Verdict verdict();

        void allow();

        void deny();
    }

    /** Context: the level. */
    interface PlacementGate extends ModuleEvent<Level>, Decided {
    }

    /** Context: the mob whose position is checked. */
    interface PositionGate extends ModuleEvent<Mob>, Decided {
    }

    /** Context: the mob that would convert. {@code cancel()} keeps it as it is. */
    interface ConversionGate extends ModuleEvent<LivingEntity> {
        void cancel();
    }

    /** The resolver of {@link PlacementGate}: the level, only while the window is open on this thread. */
    static Optional<Level> placementContext(Level level) {
        return window().open() ? Optional.of(level) : Optional.empty();
    }

    /** The resolver of {@link PositionGate}. */
    static Optional<Mob> positionContext(Mob mob) {
        return window().open() ? Optional.of(mob) : Optional.empty();
    }

    /** The resolver of {@link ConversionGate}. */
    static Optional<LivingEntity> conversionContext(LivingEntity mob) {
        return window().open() ? Optional.of(mob) : Optional.empty();
    }

    /** From a {@link PlacementGate}'s constructor, with the answer the game would give: the event's default result. */
    static void onPlacementBuilt(boolean gameAnswer) {
        Window w = window();
        w.built.incrementAndGet();
        w.gameAnswer = gameAnswer;
    }

    /** From a {@link PositionGate}'s or a {@link ConversionGate}'s constructor. */
    static void onGateBuilt() {
        window().built.incrementAndGet();
    }

    /** The global consumers each line's ingress installs. */
    static GlobalConsumers consumers() {
        GlobalConsumers consumers = new GlobalConsumers();
        consumers.listenForAll(PlacementGate.class, SpawnGateHealthCheck::decide);
        consumers.listenForAll(PositionGate.class, SpawnGateHealthCheck::decide);
        consumers.listenForAll(ConversionGate.class, e -> {
            Window w = window();
            w.delivered.incrementAndGet();
            if (w.mode == Mode.CANCEL) e.cancel();
        });
        return consumers;
    }

    /** The consumer of both result events: counts, records what it was handed, then acts by mode. */
    private static void decide(Decided e) {
        Window w = window();
        w.delivered.incrementAndGet();
        w.arrival = e.verdict();
        switch (w.mode) {
            case DENY -> e.deny();
            case ALLOW -> e.allow();
            default -> { }
        }
    }

    // =================================================================
    // The driver
    // =================================================================

    private record Run(boolean answer, int built, int delivered, ModuleSignal.Verdict arrival) {}

    /** Opens the window, makes the call, closes the window, and returns what happened. */
    private static Run run(Mode mode, BooleanSupplier call) {
        Window w = window();
        w.begin(mode);
        try {
            boolean answer = call.getAsBoolean();
            return new Run(answer, w.built.get(), w.delivered.get(), w.arrival);
        } finally {
            w.end();
        }
    }

    private static void expectOne(String what, Run run) {
        if (run.built() != 1 || run.delivered() != 1) {
            violation(what + " built " + run.built() + " and delivered " + run.delivered()
                    + " module event(s), expected exactly 1 of each");
        }
    }

    /** A call with no window open: the resolver must refuse it before anything is built. */
    private static void expectNoneOutsideWindow(String what, BooleanSupplier call) {
        Window w = window();
        w.built.set(0);
        call.getAsBoolean();
        if (w.built.get() != 0) {
            violation(what + " with no window open built " + w.built.get() + " module event(s), expected 0");
        }
    }

    /**
     * On the server's level host, in the Overworld: once the spawn area has loaded, makes the calls and checks what
     * each did.
     */
    public static final class GateDriver implements Ticking {

        /** Ticks to wait before the first try, so the probe does not run in the world's first moments. */
        private static final int START_AFTER_TICKS = 100;

        private final Loader loader;
        private int ticks;
        private boolean done;

        GateDriver(Loader loader) {
            this.loader = loader;
        }

        @Override
        public void onTickAfter(ScopeInfo info) {
            if (done) return;
            if (!(info.scope() instanceof LevelScope levelScope)) return;
            Level level = levelScope.level();
            if (!(level instanceof ServerLevelAccessor serverLevel)) return;
            if (!level.dimension().equals(Level.OVERWORLD)) return;
            if (++ticks < START_AFTER_TICKS) return;

            BlockPos pos = level.getSharedSpawnPos();
            if (!level.isLoaded(pos)) return;

            done = true;
            if (!SatchelHealth.firstTime("spawngates:driven")) return;

            placement(level, serverLevel, pos);

            Mob mob = MobHealthCheck.createSyntheticMob(level);
            if (mob == null) {
                OUT.warn("[SatchelHealth] Spawn-gate probe: could not create the throwaway mob, so the position "
                        + "and conversion events were not exercised");
                return;
            }
            position(level, serverLevel, mob);
            conversion(mob);
        }

        private static void placement(Level level, ServerLevelAccessor serverLevel, BlockPos pos) {
            BooleanSupplier call = () -> SpawnPlacements.checkSpawnRules(
                    EntityType.BAT, serverLevel, MobSpawnType.NATURAL, pos, level.getRandom());

            Run untouched = run(Mode.OBSERVE, call);
            expectOne("an untouched placement check", untouched);
            boolean gameAnswer = window().gameAnswer;
            if (untouched.arrival() == ModuleSignal.Verdict.DEFAULT && untouched.answer() != gameAnswer) {
                violation("an untouched placement check changed the game's answer from " + gameAnswer
                        + " to " + untouched.answer());
            }

            Run denied = run(Mode.DENY, call);
            expectOne("a denied placement check", denied);
            if (denied.answer()) violation("a placement check the consumer denied still allowed the spawn");

            Run allowed = run(Mode.ALLOW, call);
            expectOne("an allowed placement check", allowed);
            if (allowed.arrival() != ModuleSignal.Verdict.DENY && !allowed.answer()) {
                violation("a placement check the consumer allowed still refused the spawn");
            }

            expectNoneOutsideWindow("a placement check", call);

            SatchelHealth.logOnce("spawngates:placement", "[SatchelHealth] Spawn-gate probe: a real "
                    + "placement check, through SpawnPlacements.checkSpawnRules, built and delivered exactly 1 "
                    + "module event per call; an untouched one kept the game's own answer, a deny stopped the "
                    + "spawn, an allow forced it through, and a call with no window open built none");
        }

        private void position(Level level, ServerLevelAccessor serverLevel, Mob mob) {
            BooleanSupplier call = () -> loader.checkSpawnPosition(mob, serverLevel);
            boolean game = mob.checkSpawnRules(level, MobSpawnType.NATURAL) && mob.checkSpawnObstruction(level);

            Run untouched = run(Mode.OBSERVE, call);
            expectOne("an untouched position check", untouched);
            if (untouched.arrival() == ModuleSignal.Verdict.DEFAULT && untouched.answer() != game) {
                violation("an untouched position check changed the game's answer from " + game + " to "
                        + untouched.answer());
            }

            Run denied = run(Mode.DENY, call);
            expectOne("a denied position check", denied);
            if (denied.answer()) violation("a position check the consumer denied still allowed the position");

            Run allowed = run(Mode.ALLOW, call);
            expectOne("an allowed position check", allowed);
            if (allowed.arrival() != ModuleSignal.Verdict.DENY && !allowed.answer()) {
                violation("a position check the consumer allowed still refused the position");
            }

            expectNoneOutsideWindow("a position check", call);

            SatchelHealth.logOnce("spawngates:position", "[SatchelHealth] Spawn-gate probe: a real position check, "
                    + "through the loader's position check, built and delivered exactly 1 module event per "
                    + "call; an untouched one kept the game's own answer, a deny stopped the position, an allow "
                    + "forced it through, and a call with no window open built none");
        }

        private void conversion(Mob mob) {
            BooleanSupplier call = () -> loader.canConvert(mob, EntityType.ZOMBIE);

            Run untouched = run(Mode.OBSERVE, call);
            expectOne("an untouched pre-conversion event", untouched);
            if (!untouched.answer()) violation("a conversion the consumer only watched was stopped");

            Run canceled = run(Mode.CANCEL, call);
            expectOne("a canceled pre-conversion event", canceled);
            if (canceled.answer()) violation("a conversion the consumer canceled still went ahead");

            expectNoneOutsideWindow("a pre-conversion event", call);

            SatchelHealth.logOnce("spawngates:conversion", "[SatchelHealth] Spawn-gate probe: a real "
                    + "pre-conversion event, through the loader's conversion check, built and delivered "
                    + "exactly 1 module event per call; an untouched one let the conversion go, a cancel stopped "
                    + "it, and a call with no window open built none");
        }
    }
}
