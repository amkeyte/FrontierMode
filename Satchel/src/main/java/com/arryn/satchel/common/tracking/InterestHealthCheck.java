package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.JigInfo;
import com.arryn.satchel.common.jig.guts.LeaveReason;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.mob.MobJig;
import com.arryn.satchel.common.jig.mob.MobJigConfig;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.trinket.Leaving;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The interest API's slice of SatchelHealth (SAT_124) -- see {@link SatchelHealth}. A mob jig of its
 * own (server only, cap 3, one {@link ProbeTrinket}) and a scripted run against one synthetic mob,
 * driven from the Overworld's level scope on the server. The run confirms, loudly (a failed check
 * is logged as a violation and the run goes on, unless it cannot):
 * <ol>
 *   <li><b>No interest, no scope.</b> {@code scopeFor} is empty for a mob not on the list, and
 *   registers nothing;</li>
 *   <li><b>request-built.</b> {@code interest} builds the scope inside the call: it is {@code LOADED},
 *   a Trinket lookup answers in that same call, and a re-entrant {@code scopeFor} from the Trinket's
 *   {@code onAttach} returns the scope being loaded;</li>
 *   <li><b>idempotent.</b> A second {@code interest} returns the one scope and attaches nothing
 *   again;</li>
 *   <li><b>{@code release}.</b> Unloads at once, {@code onLeave} then {@code onDetach}, the interest
 *   unchanged;</li>
 *   <li><b>sweep-built.</b> With no request, the sweep rebuilds the scope within a few ticks;</li>
 *   <li><b>{@code lostInterest} inside a handler.</b> Called from the rebuilt scope's own {@code
 *   onAttach}, it takes effect at once and does not unload the scope;</li>
 *   <li><b>{@code drop}.</b> Ends the interest and the scope, and the sweep does not bring it back;</li>
 *   <li><b>the cap.</b> A fourth entry throws in a development run and warns in release.</li>
 * </ol>
 */
final class InterestHealthCheck {

    private InterestHealthCheck() {}

    static final JigKey<MobJig> PROBE_JIG =
            new JigKey<>("satcheltracker:interest_probe_jig", MobJig.class);

    private static final int CAP = 3;

    /** The run starts once the foundation has ticked this many times, so the world has settled. */
    private static final long START_AFTER_TICK = 100;

    /** How many ticks the sweep gets to rebuild a scope, and to be seen not to. */
    private static final long SWEEP_TICKS = 3;

    static void register() {
        Satchel.registerJig(JigPolicies.SideApplicability.SERVER, () -> build());
    }

    private static MobJigConfig build() {
        MobJigConfig config = new MobJigConfig(PROBE_JIG);
        config.bundles().schema(new JigBundles.Schema<>(List.of()));
        config.policies().maxInterest(CAP);
        config.trinkets().declare(ProbeTrinket.class, ProbeTrinket::new);
        return config;
    }

    private static void violation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: Interest " + message);
    }

    private static void check(boolean ok, String message) {
        if (!ok) violation(message);
    }

    /** Ends a run that cannot go on after a violation: its later steps need what the failed one should have made. */
    private static void abandon(Run run, MobJig jig) {
        jig.lostInterest(run.mob);
        run.entity.discard();
        run.done = true;
    }

    /**
     * One scripted run, kept in the world's health state.
     */
    static final class Run {
        int step;
        boolean done;
        long deadline;

        Mob entity;
        UUID mob;
        ScopeInfo firstScope;

        int attaches;
        final List<String> events = new ArrayList<>();
        final List<LeaveReason> reasons = new ArrayList<>();
        final List<Boolean> reentrantSame = new ArrayList<>();

        boolean lostInterestInHandler;
        boolean handlerSawNotInterested;
    }

    // =================================================================
    // The probe's Trinket
    // =================================================================

    /**
     * Held by every scope of the probe jig; acts only for the probe's own mob, since other modules'
     * mobs can reach any mob jig.
     */
    public static final class ProbeTrinket implements Leaving {

        @Override
        public void onAttach(ScopeInfo info) {
            Run run = SatchelHealth.state().interestProbe;
            if (run == null || !info.scopeId().equals(run.mob)) return;

            run.attaches++;
            run.events.add("attach");

            // A scopeFor from inside onAttach finds the scope being loaded; it builds no second one.
            MobJig jig = Satchel.mobJig(PROBE_JIG);
            Optional<ScopeInfo> again = jig.infoFor(run.mob);
            run.reentrantSame.add(again.isPresent() && again.get() == info);

            if (run.lostInterestInHandler && run.attaches == 2) {
                jig.lostInterest(run.mob);
                run.handlerSawNotInterested = !jig.isInterested(run.mob);
            }
        }

        @Override
        public void onLeave(ScopeInfo info, LeaveReason reason) {
            Run run = SatchelHealth.state().interestProbe;
            if (run == null || !info.scopeId().equals(run.mob)) return;
            run.events.add("leave");
            run.reasons.add(reason);
        }

        @Override
        public void onDetach(ScopeInfo info) {
            Run run = SatchelHealth.state().interestProbe;
            if (run == null || !info.scopeId().equals(run.mob)) return;
            run.events.add("detach");
        }
    }

    // =================================================================
    // The script
    // =================================================================

    /**
     * Called every tick by the canary Trinket on a server level scope; acts only for the Overworld's.
     */
    static void drive(ScopeInfo level) {
        if (!(level.scope() instanceof LevelScope levelScope)) return;
        if (!(levelScope.level() instanceof ServerLevel server)) return;
        if (!server.dimension().equals(Level.OVERWORLD)) return;
        if (level.jigInfo().foundation().askJigInfo(PROBE_JIG).isEmpty()) return;

        long tick = level.jigInfo().trinketTick().tickNumber();
        HealthState state = SatchelHealth.state();
        Run run = state.interestProbe;
        if (run == null) {
            if (tick < START_AFTER_TICK) return;
            run = state.interestProbe = new Run();
        }
        if (run.done) return;

        step(level, server, run, tick);
    }

    private static void step(ScopeInfo level, ServerLevel server, Run run, long tick) {
        MobJig jig = Satchel.mobJig(PROBE_JIG);
        JigInfo ji = level.jigInfo().foundation().requireJigInfo(PROBE_JIG);

        switch (run.step) {
            case 0 -> {   // the mob
                Mob mob = MobHealthCheck.spawnSyntheticMob(server);
                if (mob == null) return;   // try again next tick
                run.entity = mob;
                run.mob = mob.getUUID();
                run.step = 1;
            }

            case 1 -> {   // no interest, no scope
                check(jig.scopeFor(run.mob).isEmpty(), "scopeFor was not empty for a mob not on the list");
                check(!jig.isInterested(run.mob) && jig.interestCount() == 0,
                        "scopeFor registered interest as a side effect");
                check(!ji.hasScope(run.mob), "a scope existed for a mob nobody had registered interest in");
                run.step = 2;
            }

            case 2 -> {   // request-built
                Optional<MobScope> scope = jig.interest(run.mob);
                if (scope.isEmpty()) {
                    violation("interest() returned no scope for a mob that is in the level");
                    abandon(run, jig);
                    return;
                }
                ScopeInfo info = ji.scopeInfo(run.mob).orElseThrow();
                run.firstScope = info;
                check(info.phase() == ScopeInfo.Phase.LOADED,
                        "the scope interest() built was " + info.phase() + ", not LOADED, when the call returned");
                check(Satchel.trinket(PROBE_JIG, scope.get(), ProbeTrinket.class).isPresent(),
                        "a Trinket lookup was empty in the same call that built the scope");
                check(run.attaches == 1, "the Trinket attached " + run.attaches + " times, expected once");
                check(run.reentrantSame.equals(List.of(true)),
                        "a scopeFor from onAttach did not return the scope being loaded: " + run.reentrantSame);
                run.step = 3;
            }

            case 3 -> {   // idempotent
                jig.interest(run.mob);
                check(jig.infoFor(run.mob).orElse(null) == run.firstScope,
                        "a second interest() did not return the one scope");
                check(jig.interestCount() == 1, "interest() added a duplicate entry");
                check(run.attaches == 1, "a second interest() attached the Trinket again");
                run.step = 4;
            }

            case 4 -> {   // release
                run.events.clear();
                run.lostInterestInHandler = true;   // the rebuilt scope's onAttach drops interest
                jig.release(run.mob);
                check(!ji.hasScope(run.mob), "release() left the scope in the jig");
                check(run.events.equals(List.of("leave", "detach")),
                        "release() ran the Trinket as " + run.events + ", expected leave then detach");
                check(jig.isInterested(run.mob), "release() ended the interest; it should leave it as it was");
                run.deadline = tick + SWEEP_TICKS;
                run.step = 5;
            }

            case 5 -> {   // sweep-built
                if (!ji.hasScope(run.mob)) {
                    if (tick > run.deadline) {
                        violation("the sweep did not rebuild a released mob's scope in " + SWEEP_TICKS + " ticks");
                        abandon(run, jig);
                    }
                    return;
                }
                check(run.attaches == 2, "the rebuilt scope attached " + run.attaches + " Trinkets in total");
                check(ji.scopeInfo(run.mob).orElseThrow() != run.firstScope, "the sweep returned the released scope");
                check(run.handlerSawNotInterested,
                        "lostInterest() inside a handler did not take effect at once");
                check(!jig.isInterested(run.mob), "interest remained after lostInterest() in a handler");
                run.step = 6;
            }

            case 6 -> {   // lostInterest does not unload
                check(ji.hasScope(run.mob), "lostInterest() unloaded the live scope");
                run.step = 7;
            }

            case 7 -> {   // drop
                jig.drop(run.mob);
                check(!ji.hasScope(run.mob), "drop() left the scope in the jig");
                check(!jig.isInterested(run.mob), "drop() left the interest");
                run.deadline = tick + SWEEP_TICKS;
                run.step = 8;
            }

            case 8 -> {   // the sweep does not bring a dropped mob back
                check(!ji.hasScope(run.mob), "the sweep rebuilt the scope of a mob nobody is interested in");
                if (tick >= run.deadline) run.step = 9;
            }

            case 9 -> {   // the cap
                check(jig.interestCount() == 0, "interest remained before the cap check");
                List<UUID> fakes = new ArrayList<>();
                for (int i = 0; i < CAP; i++) {
                    UUID fake = UUID.randomUUID();
                    fakes.add(fake);
                    jig.interest(fake);
                }
                check(jig.interestCount() == CAP, "the first " + CAP + " entries were not all accepted");

                UUID over = UUID.randomUUID();
                boolean threw = false;
                try {
                    jig.interest(over);
                } catch (IllegalStateException expected) {
                    threw = true;
                }
                if (Satchel.isDevelopment()) {
                    check(threw, "entry " + (CAP + 1) + " past the cap of " + CAP + " did not throw in a development run");
                    check(jig.interestCount() == CAP, "a refused entry was added anyway");
                } else {
                    check(!threw, "entry past the cap threw in a release run, which should warn");
                }
                jig.lostInterest(over);   // idempotent: clears the entry if a failed check let it in
                for (UUID fake : fakes) jig.lostInterest(fake);

                run.entity.discard();
                run.done = true;
                OUT.info("[SatchelHealth] Interest probe passed: not-admitted scopeFor was empty, interest() built "
                        + "the scope in the call (Trinket lookup and re-entrant scopeFor answered), a second "
                        + "interest() was idempotent, release() ran leave then detach, the sweep rebuilt the scope, "
                        + "lostInterest() in a handler took effect at once without unloading it, drop() removed it for "
                        + "good, and the cap of " + CAP + " held (reasons seen: " + run.reasons + ")");
            }

            default -> run.done = true;
        }
    }
}
