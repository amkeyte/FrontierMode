package com.arryn.satchel.common.jig.guts;

import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.JigKey;
import com.arryn.satchel.common.lifecycle.SatchelEventBus;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * What module code sees of one scope: who it is, whether it is ready, its bundles and the module events
 * for it. Satchel hands one to every Trinket callback, every {@code ScopeEvent} and the scope lookups
 * ({@code Satchel.tryReadyScopeInfo}, {@code Scopes.of}, {@code MobJig.infoFor}); module code never
 * creates one and cannot change it. The lifecycle phase, the jig's configuration and the owning jig are
 * Satchel's own, on {@link ScopeInfoImpl}.
 */
public interface ScopeInfo {

    /**
     * The scope this describes: a level, a player or a mob.
     */
    SatchelScope scope();

    /**
     * A short label for logs and messages.
     */
    String debugName();

    /**
     * The key of the jig this scope belongs to. What a handler on the shared event bus compares first,
     * since every jig's scopes reach every handler.
     */
    JigKey<?> jigKey();

    /**
     * {@link #scope()} as the scope type the caller expects. Unchecked: a scope of another kind fails
     * with a {@code ClassCastException} at the call site.
     */
    @SuppressWarnings("unchecked")
    <S extends SatchelScope> S scopeAs();

    /**
     * Why this scope is unloading, or null if it is not (yet) or its kind has no reason. Set by the
     * mob jig before {@code ScopeEvent.Unloaded} fires, so an {@code Unloaded} handler can read it;
     * advisory (see {@link LeaveReason}).
     */
    LeaveReason leaveReason();

    /**
     * Whether the scope is ready under its jig's readiness policy. Every jig kind's default is
     * immediate, so a scope is ready from the moment it exists.
     */
    boolean isReady();

    /**
     * The scope's {@link Level}, for every scope kind and on both sides: a level scope is its level;
     * a player or mob scope is that entity's current level. Does not check readiness.
     *
     * @throws IllegalStateException for a scope kind this method doesn't know
     */
    Level level();

    /**
     * This scope's bundle, through the jig's coupler -- the same readiness gate and hooks as every
     * other bundle read; empty if the scope isn't ready or the bundle doesn't exist yet.
     */
    <B extends SatchelBundle> Optional<B> ask(BundleKey<B> key);

    /**
     * This scope's bundle, created and hydrated if it doesn't exist yet, through the jig's coupler:
     * the same readiness gate and hooks as every other bundle access.
     *
     * <p>
     * For a Trinket that needs persisted state at attach. Trinkets are constructed when the scope
     * loads, before the module's own {@code Loaded} handler creates its bundles, so {@link #ask} is
     * empty in {@code onAttach}; this creates the bundle there instead, and the module's later
     * {@code getOrCreate} finds it. On the server the bundle hydrates from saved data in the call; on
     * the client it is created and waits for its parcel as it always does.
     *
     * @throws SatchelException.AccessFailed if the scope is not ready under its coupler. A jig with
     *         {@code IMMEDIATE} readiness (every jig kind's default) is ready at attach; one with
     *         {@code DEFERRED} readiness is not until its scope has loaded.
     */
    <B extends SatchelBundle> B getOrCreate(BundleKey<B> key);

    /**
     * Hears a module event for this scope only: {@code handler} runs when an event of exactly {@code
     * type} is posted with this scope as its context, at normal priority, and not for a cancelled
     * event.
     *
     * <p>
     * Owned by the scope: Satchel removes the subscription when the scope unloads, even if the
     * subscriber forgets. The handle ends it sooner. Typically called from a Trinket's {@code
     * onAttach}.
     *
     * @return a handle for unsubscribing sooner
     */
    <E> SatchelEventBus.Subscription listenFor(Class<E> type, Consumer<? super E> handler);
}
