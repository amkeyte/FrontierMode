package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.IngressConfig;
import com.arryn.satchel.common.ingress.IngressKey;
import com.arryn.satchel.common.ingress.ModuleSignal;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.platform.PlatformBound;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Three real Forge events a module gates a spawn or a conversion with, driven end to end (SAT_115) -- see
 * {@link SatchelHealth}. Each is a module event on a real native event, with the same shape a game module gives
 * it: a resolver that filters, a context (the level, the mob, the converting mob) and one consumer. Server only.
 * <ul>
 *   <li><b>{@code MobSpawnEvent.SpawnPlacementCheck}</b>, a result event, through vanilla's own {@code
 *   SpawnPlacements.checkSpawnRules}, so the firing travels the path the game uses;</li>
 *   <li><b>{@code MobSpawnEvent.PositionCheck}</b>, a result event, through {@code
 *   ForgeEventFactory.checkSpawnPosition};</li>
 *   <li><b>{@code LivingConversionEvent.Pre}</b>, cancelable, through {@code
 *   ForgeEventFactory.canLivingConvert}.</li>
 * </ul>
 * Each factory call returns a boolean, so what a consumer decided is visible in what the game would do. A probe
 * calls it three ways: <i>untouched</i> (the consumer only watches), and then with a <i>deny</i> and an <i>allow</i>
 * for the two result events, or a <i>cancel</i> for the conversion. Every call must build and deliver exactly one
 * module event, an untouched one must leave the game's own answer, a deny must stop, an allow must force through,
 * and a cancel must stop. A fourth call with no window open must build nothing, which is the cheap rejection a
 * resolver gives a hot event.
 *
 * <p>
 * The <i>window</i> is what keeps live play out of it. A resolver answers only on the thread that opened the
 * window, and only while it is open, so a natural spawn or a conversion in play is never built into an event and
 * never denied. The calls happen on the server's own thread, from the level host's tick, once per world after the
 * spawn area has loaded. The conversion probe uses a throwaway mob that never joins the world.
 */
@PlatformBound(reason = "health-probe")
final class SpawnGateHealthCheck {

    private SpawnGateHealthCheck() {}

    private static final String MODULE = "satchel_health";

    private static final IngressKey INGRESS = new IngressKey("satchelhealth:spawn_gates");

    static void register() {
        Satchel.registerIngress(JigPolicies.SideApplicability.SERVER, () -> build());
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.SERVER, Order.LATE,
                GateDriver.class, GateDriver::new);
    }

    private static void violation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: Spawn gate " + message);
    }

    // =================================================================
    // The window
    // =================================================================

    /** What the consumers do to the event under test. */
    private enum Mode { OBSERVE, DENY, ALLOW, CANCEL }

    /**
     * The probe's one open window and its counts. Everything is touched on the server thread, inside the factory
     * call the driver makes, except {@code open()}, which a resolver asks on whatever thread posts the event.
     */
    private static final class Window {
        private static volatile Thread thread;
        private static volatile Mode mode = Mode.OBSERVE;
        private static final AtomicInteger built = new AtomicInteger();
        private static final AtomicInteger delivered = new AtomicInteger();
        private static volatile ModuleSignal.Verdict arrival;
        private static volatile boolean gameAnswer;

        static boolean open() {
            return thread == Thread.currentThread();
        }

        static void begin(Mode m) {
            mode = m;
            built.set(0);
            delivered.set(0);
            arrival = null;
            thread = Thread.currentThread();
        }

        static void end() {
            thread = null;
        }
    }

    // =================================================================
    // The module events
    // =================================================================

    /** Context: the level. Records the answer the game would give, which is the event's default result. */
    static final class PlacementGate extends ModuleSignal<MobSpawnEvent.SpawnPlacementCheck, Level> {
        PlacementGate(MobSpawnEvent.SpawnPlacementCheck e, Level level) {
            super(e, level);
            Window.built.incrementAndGet();
            Window.gameAnswer = e.getDefaultResult();
        }
    }

    /** Context: the mob whose position is checked. */
    static final class PositionGate extends ModuleSignal<MobSpawnEvent.PositionCheck, Mob> {
        PositionGate(MobSpawnEvent.PositionCheck e, Mob mob) {
            super(e, mob);
            Window.built.incrementAndGet();
        }
    }

    /** Context: the mob that would convert. {@link #cancel()} keeps it as it is. */
    static final class ConversionGate extends ModuleSignal<LivingConversionEvent.Pre, LivingEntity> {
        ConversionGate(LivingConversionEvent.Pre e, LivingEntity mob) {
            super(e, mob);
            Window.built.incrementAndGet();
        }

        void cancel() {
            nativeEvent().setCanceled(true);
        }
    }

    private static Optional<Level> placementLevel(MobSpawnEvent.SpawnPlacementCheck e) {
        return Window.open() ? Optional.of(e.getLevel().getLevel()) : Optional.empty();
    }

    private static Optional<Mob> positionMob(MobSpawnEvent.PositionCheck e) {
        return Window.open() ? Optional.of(e.getEntity()) : Optional.empty();
    }

    private static Optional<LivingEntity> convertingMob(LivingConversionEvent.Pre e) {
        return Window.open() ? Optional.of(e.getEntity()) : Optional.empty();
    }

    static IngressConfig build() {
        IngressConfig ingress = new IngressConfig(INGRESS);

        ingress.translate(MobSpawnEvent.SpawnPlacementCheck.class, SpawnGateHealthCheck::placementLevel,
                PlacementGate::new);
        ingress.translate(MobSpawnEvent.PositionCheck.class, SpawnGateHealthCheck::positionMob, PositionGate::new);
        ingress.translate(LivingConversionEvent.Pre.class, SpawnGateHealthCheck::convertingMob, ConversionGate::new);

        ingress.listenForAll(PlacementGate.class, SpawnGateHealthCheck::decide);
        ingress.listenForAll(PositionGate.class, SpawnGateHealthCheck::decide);
        ingress.listenForAll(ConversionGate.class, e -> {
            Window.delivered.incrementAndGet();
            if (Window.mode == Mode.CANCEL) e.cancel();
        });

        return ingress;
    }

    /** The consumer of both result events: counts, records what it was handed, then acts by mode. */
    private static void decide(ModuleSignal<?, ?> e) {
        Window.delivered.incrementAndGet();
        Window.arrival = e.verdict();
        switch (Window.mode) {
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
        Window.begin(mode);
        try {
            boolean answer = call.getAsBoolean();
            return new Run(answer, Window.built.get(), Window.delivered.get(), Window.arrival);
        } finally {
            Window.end();
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
        Window.built.set(0);
        call.getAsBoolean();
        if (Window.built.get() != 0) {
            violation(what + " with no window open built " + Window.built.get() + " module event(s), expected 0");
        }
    }

    /**
     * On the server's level host, in the Overworld: once the spawn area has loaded, makes the calls and checks
     * what each did.
     */
    public static final class GateDriver implements Ticking {

        /** Ticks to wait before the first try, so the probe does not run in the world's first moments. */
        private static final int START_AFTER_TICKS = 100;

        private int ticks;
        private boolean done;

        @Override
        public void onTickAfter(ScopeInfo info) {
            if (done) return;
            if (!(info.scope() instanceof LevelScope levelScope)) return;
            if (!(levelScope.level() instanceof ServerLevel level)) return;
            if (!level.dimension().equals(Level.OVERWORLD)) return;
            if (++ticks < START_AFTER_TICKS) return;

            BlockPos pos = level.getSharedSpawnPos();
            if (!level.isLoaded(pos)) return;

            done = true;
            if (!SatchelHealth.firstTime("spawngates:driven")) return;

            placement(level, pos);

            Mob mob = MobHealthCheck.createSyntheticMob(level);
            if (mob == null) {
                OUT.warn("[SatchelHealth] Spawn-gate probe: could not create the throwaway mob, so the position "
                        + "and conversion events were not exercised");
                return;
            }
            position(level, mob);
            conversion(mob);
        }

        private static void placement(ServerLevel level, BlockPos pos) {
            BooleanSupplier call = () -> SpawnPlacements.checkSpawnRules(
                    EntityType.BAT, level, MobSpawnType.NATURAL, pos, level.getRandom());

            Run untouched = run(Mode.OBSERVE, call);
            expectOne("an untouched SpawnPlacementCheck", untouched);
            if (untouched.arrival() == ModuleSignal.Verdict.DEFAULT && untouched.answer() != Window.gameAnswer) {
                violation("an untouched SpawnPlacementCheck changed the game's answer from " + Window.gameAnswer
                        + " to " + untouched.answer());
            }

            Run denied = run(Mode.DENY, call);
            expectOne("a denied SpawnPlacementCheck", denied);
            if (denied.answer()) violation("a SpawnPlacementCheck the consumer denied still allowed the spawn");

            Run allowed = run(Mode.ALLOW, call);
            expectOne("an allowed SpawnPlacementCheck", allowed);
            if (allowed.arrival() != ModuleSignal.Verdict.DENY && !allowed.answer()) {
                violation("a SpawnPlacementCheck the consumer allowed still refused the spawn");
            }

            expectNoneOutsideWindow("a SpawnPlacementCheck", call);

            SatchelHealth.logOnce("spawngates:placement", "[SatchelHealth] Spawn-gate probe: a real "
                    + "SpawnPlacementCheck, through SpawnPlacements.checkSpawnRules, built and delivered exactly 1 "
                    + "module event per call; an untouched one kept the game's own answer, a deny stopped the "
                    + "spawn, an allow forced it through, and a call with no window open built none");
        }

        private static void position(ServerLevel level, Mob mob) {
            BooleanSupplier call = () -> ForgeEventFactory.checkSpawnPosition(mob, level, MobSpawnType.NATURAL);
            boolean game = mob.checkSpawnRules(level, MobSpawnType.NATURAL) && mob.checkSpawnObstruction(level);

            Run untouched = run(Mode.OBSERVE, call);
            expectOne("an untouched PositionCheck", untouched);
            if (untouched.arrival() == ModuleSignal.Verdict.DEFAULT && untouched.answer() != game) {
                violation("an untouched PositionCheck changed the game's answer from " + game + " to "
                        + untouched.answer());
            }

            Run denied = run(Mode.DENY, call);
            expectOne("a denied PositionCheck", denied);
            if (denied.answer()) violation("a PositionCheck the consumer denied still allowed the position");

            Run allowed = run(Mode.ALLOW, call);
            expectOne("an allowed PositionCheck", allowed);
            if (allowed.arrival() != ModuleSignal.Verdict.DENY && !allowed.answer()) {
                violation("a PositionCheck the consumer allowed still refused the position");
            }

            expectNoneOutsideWindow("a PositionCheck", call);

            SatchelHealth.logOnce("spawngates:position", "[SatchelHealth] Spawn-gate probe: a real PositionCheck, "
                    + "through ForgeEventFactory.checkSpawnPosition, built and delivered exactly 1 module event per "
                    + "call; an untouched one kept the game's own answer, a deny stopped the position, an allow "
                    + "forced it through, and a call with no window open built none");
        }

        private static void conversion(Mob mob) {
            BooleanSupplier call = () -> ForgeEventFactory.canLivingConvert(mob, EntityType.ZOMBIE, t -> { });

            Run untouched = run(Mode.OBSERVE, call);
            expectOne("an untouched LivingConversionEvent.Pre", untouched);
            if (!untouched.answer()) violation("a conversion the consumer only watched was stopped");

            Run canceled = run(Mode.CANCEL, call);
            expectOne("a canceled LivingConversionEvent.Pre", canceled);
            if (canceled.answer()) violation("a conversion the consumer canceled still went ahead");

            expectNoneOutsideWindow("a LivingConversionEvent.Pre", call);

            SatchelHealth.logOnce("spawngates:conversion", "[SatchelHealth] Spawn-gate probe: a real "
                    + "LivingConversionEvent.Pre, through ForgeEventFactory.canLivingConvert, built and delivered "
                    + "exactly 1 module event per call; an untouched one let the conversion go, a cancel stopped "
                    + "it, and a call with no window open built none");
        }
    }
}
