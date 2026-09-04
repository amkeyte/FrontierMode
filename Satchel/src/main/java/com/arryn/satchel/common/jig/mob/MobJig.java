package com.arryn.satchel.common.jig.mob;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.guts.ASatchelJig;
import com.arryn.satchel.common.jig.guts.JigInfo;
import com.arryn.satchel.common.jig.guts.ScopeCoupler;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.common.util.throttle.TickThrottler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The fourth real jig kind (RM_SAT_021, "Frank") -- the mob/entity generalization of
 * {@code Scope}. Mirrors {@link com.arryn.satchel.common.jig.player.PlayerJig}'s shape for the
 * scope/config plumbing; the one genuinely new piece is {@link #reconcile}, since presence for a
 * tagged {@link Mob} must be actively re-verified rather than assumed from a Forge join/leave
 * event -- see Jig & Scope Runtime's MobJig section for the full design.
 */
public final class MobJig extends ASatchelJig<MobScope> {

    // Roughly every 20 ticks, matching PlayerTrackingModule's own cadence precedent -- not a
    // hard requirement, tunable once there's something real to measure against. One MobJig
    // instance exists per compiled JigConfig (per JigKey), so this throttle is naturally scoped
    // per-consumer, never shared/global.
    private final TickThrottler throttler =
            new TickThrottler(20, new TickThrottler.AutoClock());

    public MobJig() {
    }

    @Override
    public Class<MobScope> scopeType() {
        return MobScope.class;
    }

    @Deprecated
    @Override
    public Class<? extends ScopeCoupler> couplerClass() {
        return MobScopeCoupler.class;
    }

    @Override
    public MobScope resolveScope(Object source) {
        Objects.requireNonNull(source, "source");
        return MobScope.resolveScope(source);
    }

    @SuppressWarnings("unchecked")
    @Override
    public JigKey<MobJig> jigKey() {
        return (JigKey<MobJig>) super.jigKey();
    }

    /**
     * Deterministic UUID for a mob scope, derived solely from the mob's own persistent UUID --
     * see {@link MobScope#resolveUUID} for the full reasoning.
     */
    @Override
    public UUID determineUUID(Object source) {
        return MobScope.resolveUUID(source);
    }

    /* =============================================================
     * Additive mob-kind signals (SAT_044)
     * ========================================================== */

    /**
     * Overrides {@link ASatchelJig#onLoad} to post {@link ScopeEvent.MobGainedInterest}
     * immediately after the generic {@link ScopeEvent.Loaded} fires. Additive -- the
     * super call is unchanged; this only appends the extra signal on the same call stack.
     */
    @Override
    public void onLoad(ScopeInfo info) {
        super.onLoad(info); // → coupler.onScopeLoad → signalScopeLoaded → ScopeEvent.Loaded
        OUT.debug("[MobJig] MobGainedInterest: " + info.scope());
        Satchel.require().eventBus().post(new ScopeEvent.MobGainedInterest(info));
    }

    /**
     * Overrides {@link ASatchelJig#onUnload} to post {@link ScopeEvent.MobLostInterest}
     * immediately after the generic {@link ScopeEvent.Unloaded} fires. Additive -- the
     * super call is unchanged; this only appends the extra signal on the same call stack.
     *
     * <p>
     * The {@link ScopeInfo} object is still valid for reading after {@code super.onUnload}
     * returns, even though the scope has been evicted from {@code JigInfo} by that point.
     */
    @Override
    public void onUnload(ScopeInfo info) {
        super.onUnload(info); // → signalScopeUnloaded → ScopeEvent.Unloaded → scope evicted
        OUT.debug("[MobJig] MobLostInterest: " + info.scope());
        Satchel.require().eventBus().post(new ScopeEvent.MobLostInterest(info));
    }

    /* =============================================================
     * Poll-driven reconciliation
     * ========================================================== */

    /**
     * Poll-driven presence reconciliation -- called once per {@link JigInfo} per foundation pulse
     * (see {@code FoundationLifecycleDispatcher.pulse()}), throttled to roughly every 20 ticks so
     * it doesn't run every single pulse. Not a new Forge tick hook: it rides the existing
     * {@code TickEvent.ServerTickEvent -> foundationLifecycle().pulse()} path every other jig
     * kind already walks.
     *
     * <p>
     * Two phases, both reason-agnostic by design (a chunk unload and a genuine removal are
     * indistinguishable via {@link com.arryn.satchel.common.jig.guts.ForgeEgress#getEntity} and
     * are treated identically):
     * <ol>
     *   <li>Introduce: for every UUID any registered {@link MobInterestSupplier} reports, resolve
     *   it via {@code Satchel.require().egress().getEntity(level, uuid)} and introduce it if it
     *   resolves to a live, non-removed {@link Mob}.</li>
     *   <li>Tear down: any UUID this jig currently has a scope for that did not resolve this
     *   cycle gets torn down.</li>
     * </ol>
     *
     * <p>
     * RM_SAT_022 ("Roger"): both phases used to cast to {@code ServerLevel} directly, which is
     * why a {@code CLIENT}/{@code BOTH}-applicability consumer's scopes were torn down every
     * cycle regardless of the mob's real state -- Phase 2's cast guard simply {@code continue}d
     * for anything that wasn't a {@code ServerLevel}. Resolving through {@code ForgeEgress}
     * instead fixes that as a consequence of unifying the mechanism, not a separate patch: a
     * client-side scope now re-resolves through {@code ClientForgeEgress} exactly like a
     * server-side one resolves through {@code ServerForgeEgress}.
     */
    @Override
    public void reconcile(JigInfo info) {
        Objects.requireNonNull(info, "info");

        if (!throttler.allow()) {
            return;
        }

        MobInterestSupplier supplier = MobInterestRegistry.get(info.key);
        Map<Level, Set<UUID>> interests = supplier.interestedMobs();

        Set<UUID> resolvedThisCycle = new HashSet<>();

        for (Map.Entry<Level, Set<UUID>> entry : interests.entrySet()) {
            Level level = entry.getKey();
            for (UUID uuid : entry.getValue()) {
                Optional<Entity> resolved = Satchel.require().egress().getEntity(level, uuid);
                if (resolved.isPresent() && resolved.get() instanceof Mob mob && !mob.isRemoved()) {
                    resolvedThisCycle.add(uuid);
                    // Idempotent via JigInfo.hasScope's existing guard -- safe to call every
                    // cycle even for a mob already scoped, same reliance MobScope.getFor has on
                    // this same guard.
                    Satchel.require().introduceSource(mob);
                }
            }
        }

        // Snapshot before tearing down: info.scopeInfos() is backed by JigInfo's own
        // ConcurrentHashMap, and onUnload(...) below mutates that same map (via
        // ScopeLifecycleDispatcher.signalScopeUnloaded -> JigInfo.removeScope). A snapshot keeps
        // this pass's view stable instead of relying on weakly-consistent iteration semantics.
        List<ScopeInfo> snapshot = new ArrayList<>(info.scopeInfos());

        // Second chance for a scope the interest walk above never touched -- most notably one
        // introduced via MobScope.getFor(), which deliberately bypasses the interest walk for
        // its immediate-attachment guarantee (see mobscope-getfor.md). Without this pass, any
        // such scope would be a teardown candidate on the very next cycle no matter how
        // resolvable the mob still is, since resolvedThisCycle above only ever gets populated
        // from registered interest -- confirmed wrong by real testing (SAT_035 log, 2026-08-22):
        // a getFor'd scope was torn down about one throttle cycle after attaching, with the mob
        // still standing right there, unmoved. So every currently-scoped UUID the interest walk
        // didn't already reconfirm gets its own direct re-resolution here, via the same
        // ForgeEgress mechanism Phase 1 uses -- reason-agnostic teardown still applies, it now
        // just applies uniformly to every scoped mob, not only interest-backed ones.
        //
        // RM_SAT_022 ("Roger"): this used to guard with `instanceof ServerLevel`, `continue`-ing
        // for anything else -- exactly why a client-scoped MobScope was torn down every cycle
        // regardless of the mob's real state. mob().level() is already Level-typed (Entity's own
        // field), so there is no cast to guard here anymore; ForgeEgress handles both sides.
        for (ScopeInfo scopeInfo : snapshot) {
            UUID scopedUuid = scopeInfo.scope().uuid();
            if (resolvedThisCycle.contains(scopedUuid)) {
                continue;
            }
            if (!(scopeInfo.scope() instanceof MobScope mobScope)) {
                continue;
            }
            Level level = mobScope.mob().level();
            Optional<Entity> reresolved = Satchel.require().egress().getEntity(level, scopedUuid);
            if (reresolved.isPresent() && reresolved.get() instanceof Mob mob && !mob.isRemoved()) {
                resolvedThisCycle.add(scopedUuid);
            }
        }

        Set<UUID> currentlyScoped = new HashSet<>();
        for (ScopeInfo scopeInfo : snapshot) {
            currentlyScoped.add(scopeInfo.scope().uuid());
        }

        Set<UUID> teardowns =
                MobReconcileLogic.computeTeardowns(resolvedThisCycle, currentlyScoped);

        if (teardowns.isEmpty()) {
            return;
        }

        for (ScopeInfo scopeInfo : snapshot) {
            if (teardowns.contains(scopeInfo.scope().uuid())) {
                // LogicalFoundation.tryRemoveSource can't be used here -- it re-resolves the
                // scope from a live source object, and there is none once a UUID stops
                // resolving. onUnload directly on the already-known ScopeInfo is the correct
                // substitute: it drives ScopeLifecycleDispatcher.signalScopeUnloaded, which
                // evicts the entry from JigInfo's own map -- no leaked ScopeInfo.
                this.onUnload(scopeInfo);
            }
        }
    }
}
