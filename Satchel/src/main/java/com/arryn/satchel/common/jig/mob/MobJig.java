package com.arryn.satchel.common.jig.mob;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.jig.guts.*;
import com.arryn.satchel.common.lifecycle.ScopeEvent;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.common.util.schedule.SchedulerClock;
import com.arryn.satchel.common.util.schedule.TickThrottler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mob jig kind. The jig keeps the interest itself: a UUID entry for each mob a module cares
 * about, whether or not the mob is in the level. When the mob is in the level the jig builds a scope
 * for it, on request ({@link #scopeFor}, {@link #interest}) and by a per-tick sweep. When the mob
 * leaves, the scope unloads, at once, and the interest stays. The module alone decides when to stop
 * caring ({@link #lostInterest}).
 *
 * <p>
 * A mob scope is a <i>live</i> scope: it exists only while its mob is in the level. Interest is
 * explicit; no call registers it as a side effect. Satchel does the same thing for every leave and
 * never acts on the {@link LeaveReason} it records.
 *
 * <p>
 * The previous model, a polled {@link MobInterestRegistry} a module registers a supplier with, still
 * runs beside this one until the modules that use it have moved: its introduction half keeps running
 * (throttled), and either an entry in this jig's list or a registration there counts as interest.
 * Its teardown half is gone: the leave event and a per-tick {@code isRemoved()} check unload scopes.
 *
 * <p>
 * Design: Mob Interest in the Satchel wiki; the contract of {@link #scopeFor} is the Mob
 * {@code scopeFor()} Contract.
 */
public final class MobJig extends ASatchelJig<MobScope> {

    // The old registry's introduction poll runs roughly every 20 ticks. One MobJig instance exists per
    // compiled JigConfig (per JigKey), so this throttle is naturally scoped per-consumer.
    private final TickThrottler legacyPoll =
            new TickThrottler(20, new SchedulerClock.AutoClock());

    private final InterestList<Level> interests = new InterestList<>();

    /**
     * The UUIDs whose scope is unloading, from the start of the unload until the last Trinket has
     * detached. While a UUID is here this jig builds no second scope for it, even though the mob is
     * still in the level (a chunk unload, a {@link #release}, a {@link #reset}). See Mob Interest,
     * "No introduction during an unload".
     */
    private final Set<UUID> unloading = ConcurrentHashMap.newKeySet();

    /** True for the whole of {@link #reset}: no scope is built, for any UUID. */
    private volatile boolean resetting;

    /** The over-cap warning in a release run is repeated this often, not on every add. */
    private static final int CAP_WARNING_EVERY = 50;

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
     * The interest API
     * ========================================================== */

    /**
     * Adds interest in {@code uuid}: from now on the jig builds a scope for this mob whenever it is
     * in the level. If it is in the level now, the scope is built in this call and returned, with
     * its Trinkets attached ({@code Loaded} has fired inside the call).
     *
     * <p>
     * Idempotent. Past the jig's cap ({@code policies().maxInterest()}, default 500) a development
     * run throws and a release run warns, naming the jig and a few UUIDs.
     *
     * <p>
     * The entry is recorded even when no scope can be built now: the mob is not in the level, its scope
     * is unloading, or the jig is resetting. The sweep builds the scope once it can.
     *
     * @return the scope, or empty if the mob is not in the level or has not joined it, or its scope is
     *         unloading (the sweep builds it when it can)
     */
    public Optional<MobScope> interest(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");

        if (interests.wouldExceed(uuid, policies().maxInterest())) {
            overCap(uuid);
        }
        interests.add(uuid);

        return scopeFor(uuid);
    }

    /**
     * The scope of an interest-listed mob that is in the level, built now if it has none. Empty
     * means "not usable right now", never an error: the foundation is not ready, the UUID is not
     * admitted (nothing is registered as a side effect), the mob is not in the level, has not joined it
     * yet or is already removed, or its scope is unloading (from the start of the unload until its
     * Trinkets have detached) or the jig is resetting, when no second scope is built. Idempotent. The
     * returned scope is not a handle to hold across ticks: when the mob leaves, the scope unloads.
     *
     * <p>
     * Must be called on a thread bound to this jig's side.
     */
    public Optional<MobScope> scopeFor(UUID uuid) {
        return infoFor(uuid).map(info -> (MobScope) info.scope());
    }

    /**
     * {@link #scopeFor} as the {@code ScopeInfo}, which is what a Trinket or a bundle lookup wants.
     */
    public Optional<ScopeInfo> infoFor(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");

        LogicalFoundation foundation = Satchel.peekFoundation();
        if (foundation == null || !foundation.isReady()) return Optional.empty();

        Optional<JigInfo> found = foundation.askJigInfo(jigKey());
        if (found.isEmpty() || found.get().jig != this) return Optional.empty();
        JigInfo info = found.get();

        if (!admitsUuid(foundation, uuid)) return Optional.empty();

        // Checked before the existing scope: a scope that is unloading is no longer available, and none
        // is rebuilt for its mob until the unload has finished.
        if (blocked(uuid)) return Optional.empty();

        Optional<ScopeInfoImpl> existing = info.scopeInfo(uuid);
        if (existing.isPresent()) return existing.map(scopeInfo -> scopeInfo);

        return resolve(foundation, uuid).flatMap(mob -> introduce(info, mob)).map(scopeInfo -> scopeInfo);
    }

    /**
     * The scope of {@code mob} if this jig admits it, built now if it has none: what a module
     * event's resolver asks ({@code Scopes.of}). An admitted mob that is in the level always gets its
     * scope, so an event for it is delivered even if it lands before the sweep has built the scope;
     * a mob this jig does not admit is empty. Empty, too, when the foundation is not ready, the mob
     * has not joined the level (its own {@code EntityJoinLevelEvent} is posted before it does, and
     * may be cancelled) or is already removed and has no scope, or its scope is unloading or the jig
     * is resetting.
     *
     * <p>
     * A mob whose UUID already has a scope gets that scope, even if it was built for another {@code
     * Mob} object (the copy a level change creates); the original's leave unloads it.
     */
    public Optional<ScopeInfo> infoFor(Mob mob) {
        Objects.requireNonNull(mob, "mob");

        LogicalFoundation foundation = Satchel.peekFoundation();
        if (foundation == null || !foundation.isReady()) return Optional.empty();

        Optional<JigInfo> found = foundation.askJigInfo(jigKey());
        if (found.isEmpty() || found.get().jig != this) return Optional.empty();

        if (!admits(mob)) return Optional.empty();
        if (blocked(mob.getUUID())) return Optional.empty();
        return introduce(found.get(), mob).map(scopeInfo -> scopeInfo);
    }

    /**
     * Whether this jig admits {@code mob}: the resolver's gate, O(1) per jig. A mob is admitted
     * whether or not it has a live scope.
     */
    public boolean admits(Mob mob) {
        Objects.requireNonNull(mob, "mob");
        UUID uuid = mob.getUUID();
        return interests.contains(uuid) || legacyAdmits(mob.level(), uuid);
    }

    /**
     * Whether {@code uuid} is on this jig's list. O(1).
     */
    public boolean isInterested(UUID uuid) {
        return interests.contains(uuid);
    }

    /**
     * Stops caring about {@code uuid}: idempotent, safe inside a handler, and effective at once. It
     * does not unload a live scope; the scope lives until its mob leaves or is {@link #release}d,
     * so a mob that dies mid-fight keeps its scope and its {@code Unloaded} handler runs the cleanup.
     * Once the mob has left, nothing rebuilds it.
     */
    public void lostInterest(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        interests.remove(uuid);
    }

    /**
     * Unloads the live scope of {@code uuid} now (it runs {@code Unloaded}, then {@code onLeave} and
     * {@code onDetach} on each Trinket). The interest is unchanged, so the sweep rebuilds the scope
     * if the mob is still in the level. An explicit unload, not a collection: scope lifetime is
     * "admitted and present", never a function of how often it was requested.
     */
    public void release(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");

        LogicalFoundation foundation = Satchel.peekFoundation();
        if (foundation == null) return;
        Optional<JigInfo> found = foundation.askJigInfo(jigKey());
        if (found.isEmpty() || found.get().jig != this) return;

        JigInfo info = found.get();
        info.scopeInfo(uuid).ifPresent(scope -> {
            LeaveReason reason = scope.scope() instanceof MobScope mobScope
                    ? LeaveReasons.of(mobScope.mob())
                    : LeaveReason.UNKNOWN;
            unload(info, scope, reason);
        });
    }

    /**
     * {@link #lostInterest} and {@link #release}.
     */
    public void drop(UUID uuid) {
        lostInterest(uuid);
        release(uuid);
    }

    /**
     * How many mobs this jig holds interest in, for the dump and the cap.
     */
    public int interestCount() {
        return interests.size();
    }

    /**
     * A copy of the listed UUIDs.
     */
    public List<UUID> interestedUuids() {
        return interests.snapshot();
    }

    /**
     * Whether any mob jig on {@code foundation} holds interest in {@code uuid}: the gate for the
     * death and removal signals, which also ask the old registry.
     */
    public static boolean anyInterested(LogicalFoundation foundation, UUID uuid) {
        for (JigInfo info : foundation.jigInfos()) {
            if (info.jig instanceof MobJig mobJig && mobJig.interests.contains(uuid)) return true;
        }
        return false;
    }

    private void overCap(UUID uuid) {
        int cap = policies().maxInterest();
        String message = "Mob jig " + jigKey().name + " would hold more than its cap of " + cap
                + " interest entries (adding " + Ids.shortId(uuid) + "). A module is not calling "
                + "lostInterest. Listed, for example: " + shortIds(interests.sample(5));

        if (Satchel.isDevelopment()) {
            throw new IllegalStateException(message);
        }
        if ((interests.size() - cap) % CAP_WARNING_EVERY == 0) {
            OUT.warn("[MobJig] " + message);
        }
    }

    private static String shortIds(List<UUID> uuids) {
        List<String> out = new ArrayList<>();
        for (UUID u : uuids) out.add(Ids.shortId(u));
        return out.toString();
    }

    /* =============================================================
     * Introduction
     * ========================================================== */

    /**
     * Builds and loads the scope for {@code mob}, in the caller's call stack, and returns it.
     * Idempotent through the UUID index. The scope is in the jig's list before {@code onScopeLoad}
     * runs, so a {@code scopeFor} or {@code interest} call from an {@code onAttach} or a {@code
     * Loaded} handler finds it and returns it rather than building a second one. When this returns,
     * a normally loading scope is {@code LOADED} with its Trinkets attached.
     *
     * <p>
     * Builds only for a mob that has joined the level and is not removed: Forge sets {@code
     * isAddedToWorld()} after the join, and posts the mob's own {@code EntityJoinLevelEvent} before it,
     * where the join can still be cancelled or refused for a duplicate UUID, so a scope built then
     * could exist for a mob that never joined. Nor does it build while the UUID's scope is unloading
     * or the jig is resetting.
     */
    private Optional<ScopeInfoImpl> introduce(JigInfo info, Mob mob) {
        Optional<ScopeInfoImpl> existing = info.scopeInfo(mob.getUUID());
        if (existing.isPresent()) return existing;
        if (blocked(mob.getUUID())) return Optional.empty();
        if (!mob.isAddedToWorld() || mob.isRemoved()) return Optional.empty();

        return Optional.of(build(info, mob));
    }

    /**
     * Whether no scope may be built for {@code uuid} right now: its scope is unloading, or the jig is
     * resetting.
     */
    private boolean blocked(UUID uuid) {
        return resetting || unloading.contains(uuid);
    }

    private ScopeInfoImpl build(JigInfo info, Mob mob) {
        ScopeInfoImpl scopeInfo = info.addScope(new MobScope(mob), mob);
        // Converge now: the readiness check, onScopeLoad (Trinkets attached), then Loaded. A mob jig's
        // readiness is IMMEDIATE, so nothing waits for the next pulse. A scope the pulse finds already
        // LOADED skips this branch.
        handleExecutionPulse(scopeInfo);
        return scopeInfo;
    }

    /**
     * Looks {@code uuid} up through the egress: the level the mob was last seen in first, then the
     * others. Empty unless it is a live, non-removed {@link Mob}.
     */
    private Optional<Mob> resolve(LogicalFoundation foundation, UUID uuid) {
        ForgeEgress egress = foundation.egress();

        Level hint = interests.hint(uuid);
        if (hint != null) {
            Optional<Mob> found = liveMob(egress.getEntity(hint, uuid));
            if (found.isPresent()) return found;
        }

        for (Level level : egress.levels()) {
            if (level == hint) continue;
            Optional<Mob> found = liveMob(egress.getEntity(level, uuid));
            if (found.isPresent()) {
                interests.setHint(uuid, level);
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<Mob> liveMob(Optional<Entity> entity) {
        if (entity.isPresent() && entity.get() instanceof Mob mob && !mob.isRemoved()) {
            return Optional.of(mob);
        }
        return Optional.empty();
    }

    private boolean admitsUuid(LogicalFoundation foundation, UUID uuid) {
        return interests.contains(uuid) || legacyAdmits(foundation, null, uuid);
    }

    /* =============================================================
     * Teardown
     * ========================================================== */

    /**
     * A mob left its level (the {@code EntityLeaveLevelEvent}, from Satchel's ingress). Unloads this
     * jig's scope for it. An admitted mob that leaves with no scope has one built, then unloaded, so
     * {@code Unloaded} fires and {@code Leaving} is called with a reason for every admitted mob that
     * was in the level. The interest is untouched.
     *
     * <p>
     * A scope built for a different {@code Mob} object with the same UUID is left alone: that is the
     * destination of a level change, not this leave.
     */
    public void onMobLeft(JigInfo info, Mob mob) {
        Optional<ScopeInfoImpl> existing = info.scopeInfo(mob.getUUID());

        if (existing.isPresent()) {
            ScopeInfoImpl scopeInfo = existing.get();
            if (scopeInfo.scope() instanceof MobScope mobScope && mobScope.mob() != mob) return;
            unload(info, scopeInfo, LeaveReasons.of(mob));
            return;
        }

        // A leave event from inside an unload (a detach that removes the mob) must not build a second
        // scope just to unload it.
        if (!admits(mob) || blocked(mob.getUUID())) return;
        unload(info, build(info, mob), LeaveReasons.of(mob));
    }

    /**
     * Unloads {@code scopeInfo} for {@code reason}: records the reason where {@code Unloaded}
     * handlers and {@code Leaving} Trinkets can read it, then the generic unload.
     */
    private void unload(JigInfo info, ScopeInfoImpl scopeInfo, LeaveReason reason) {
        if (scopeInfo.phase() == ScopeInfoImpl.Phase.UNLOADING || scopeInfo.phase() == ScopeInfoImpl.Phase.UNLOADED) {
            return;
        }

        scopeInfo.setLeaveReason(reason);

        if (scopeInfo.phase() == ScopeInfoImpl.Phase.NEW) {
            // Never loaded: no Loaded was posted and no Trinket attached, so there is nothing to
            // unload, only a scope to forget.
            info.removeScope(scopeInfo.scope());
            return;
        }

        // From here until the last Trinket has detached, no second scope is built for this mob, even
        // though it is still in the level. Removed in a finally: a throw in a detach must not leave
        // the UUID blocked.
        UUID uuid = scopeInfo.scopeId();
        unloading.add(uuid);
        try {
            this.onUnload(scopeInfo);
        } finally {
            unloading.remove(uuid);
        }
    }

    /* =============================================================
     * Reset
     * ========================================================== */

    /**
     * Unloads every scope and clears the interest, so nothing outlives its world. The foundation
     * calls this when a server stops or a client logs out.
     */
    @Override
    public void reset(JigInfo info) {
        // For the whole reset no scope is built, for any UUID: one rebuilt by a detach would not be in
        // this loop's snapshot and would survive into the next world. An interest recorded meanwhile
        // is cleared with the rest.
        resetting = true;
        try {
            for (ScopeInfoImpl scopeInfo : new ArrayList<>(info.scopeInfos())) {
                unload(info, scopeInfo, LeaveReason.UNKNOWN);
            }
            interests.clear();
        } finally {
            resetting = false;
        }
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
    public void onLoad(ScopeInfoImpl info) {
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
     * The {@link ScopeInfoImpl} object is still valid for reading after {@code super.onUnload}
     * returns, even though the scope has been evicted from {@code JigInfo} by that point.
     */
    @Override
    public void onUnload(ScopeInfoImpl info) {
        super.onUnload(info); // → signalScopeUnloaded → ScopeEvent.Unloaded → scope evicted
        OUT.debug("[MobJig] MobLostInterest: " + info.scope());
        Satchel.require().eventBus().post(new ScopeEvent.MobLostInterest(info));
    }

    /* =============================================================
     * Per-pulse work
     * ========================================================== */

    /**
     * Called once per {@link JigInfo} per foundation pulse, in this jig's slot of the internal walk
     * (see {@code FoundationLifecycleDispatcher.pulse()}). Three duties:
     * <ol>
     *   <li><b>Teardown check.</b> A live scope whose mob has been removed unloads, with the mob's
     *   removal reason. Needs no lookup. The leave event unloads most scopes before this runs; this
     *   is the backstop.</li>
     *   <li><b>The sweep.</b> Each interest entry with no scope is tried against the levels, the
     *   last level seen first, and a scope is built for a mob that is in the level. It builds scopes
     *   for mobs no event touches, so their Trinkets tick, and catches a mob whose chunk's tracking
     *   ended and restarted before the chunk unloaded and one that joined while its chunk was not
     *   yet accessible. Every tick.</li>
     *   <li><b>The old registry's introduction poll,</b> about every 20 ticks, for modules that
     *   still register a supplier.</li>
     * </ol>
     */
    @Override
    public void reconcile(JigInfo info) {
        Objects.requireNonNull(info, "info");

        // A snapshot: unloading a scope removes it from the map this walk reads.
        for (ScopeInfoImpl scopeInfo : new ArrayList<>(info.scopeInfos())) {
            if (scopeInfo.scope() instanceof MobScope mobScope && mobScope.mob().isRemoved()) {
                unload(info, scopeInfo, LeaveReasons.of(mobScope.mob()));
            }
        }

        sweep(info);

        if (legacyPoll.allow()) {
            introduceFromRegistry(info);
        }
    }

    private void sweep(JigInfo info) {
        if (interests.size() == 0 || resetting) return;

        LogicalFoundation foundation = info.foundation();
        for (UUID uuid : interests.snapshot()) {
            // An entry lostInterest removed since the snapshot is no longer wanted; one whose scope is
            // unloading is built again once the unload has finished.
            if (!interests.contains(uuid) || info.hasScope(uuid) || blocked(uuid)) continue;

            resolve(foundation, uuid).ifPresent(mob -> introduce(info, mob));
        }
    }

    /**
     * The old model's introduction half: every UUID a registered {@link MobInterestSupplier} or
     * {@link MobInterestPerLevel} reports that resolves to a live, non-removed {@link Mob} is
     * introduced the way it always was, through the foundation, and so reaches every mob jig.
     * Its teardown half is gone; the leave event does that now.
     */
    private void introduceFromRegistry(JigInfo info) {
        MobInterestSupplier supplier = MobInterestRegistry.get(info.key);
        Map<Level, Set<UUID>> registered = supplier.interestedMobs();

        for (Map.Entry<Level, Set<UUID>> entry : registered.entrySet()) {
            introduceResolvable(entry.getKey(), entry.getValue());
        }

        // Per-level registration (SAT_098), beside the old form: asked once for each level this
        // side has. A live view of the module's own set, so nothing is copied here.
        MobInterestPerLevel perLevel = MobInterestRegistry.getPerLevel(info.key);
        if (perLevel != null) {
            for (Level level : Satchel.require().egress().levels()) {
                introduceResolvable(level, perLevel.interestedIn(level));
            }
        }
    }

    private static void introduceResolvable(Level level, Set<UUID> uuids) {
        for (UUID uuid : uuids) {
            Optional<Entity> entity = Satchel.require().egress().getEntity(level, uuid);
            if (entity.isPresent() && entity.get() instanceof Mob mob && !mob.isRemoved()) {
                // Idempotent via JigInfo.hasScope's existing guard -- safe to call every cycle even
                // for a mob already scoped.
                Satchel.require().introduceSource(mob);
            }
        }
    }

    /* =============================================================
     * The old registry as interest
     * ========================================================== */

    /**
     * Whether the old registry names {@code uuid} for this jig's key, in {@code level} (or in any
     * level, if null).
     */
    private boolean legacyAdmits(Level level, UUID uuid) {
        return legacyAdmits(Satchel.peekFoundation(), level, uuid);
    }

    private boolean legacyAdmits(LogicalFoundation foundation, Level level, UUID uuid) {
        JigKey<MobJig> key = jigKey();

        MobInterestSupplier supplier = MobInterestRegistry.get(key);
        Map<Level, Set<UUID>> registered = supplier.interestedMobs();
        if (level != null) {
            Set<UUID> set = registered.get(level);
            if (set != null && set.contains(uuid)) return true;
        } else {
            for (Set<UUID> set : registered.values()) {
                if (set.contains(uuid)) return true;
            }
        }

        MobInterestPerLevel perLevel = MobInterestRegistry.getPerLevel(key);
        if (perLevel == null) return false;
        if (level != null) return perLevel.interestedIn(level).contains(uuid);
        if (foundation == null) return false;
        for (Level each : foundation.egress().levels()) {
            if (perLevel.interestedIn(each).contains(uuid)) return true;
        }
        return false;
    }
}
