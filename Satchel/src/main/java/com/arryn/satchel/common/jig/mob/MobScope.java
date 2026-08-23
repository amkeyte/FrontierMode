package com.arryn.satchel.common.jig.mob;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.jig.guts.ASatchelScope;
import com.arryn.satchel.common.stitch.PersistStitch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SatchelScope representing a tagged {@link Mob} -- the fourth jig kind (RM_SAT_021, "Frank").
 * Mirrors {@link com.arryn.satchel.common.jig.player.PlayerScope}'s shape for the scope/config
 * plumbing, with two deliberate departures documented on Jig & Scope Runtime's MobJig section and
 * this class's own {@link #getFor} contract:
 * <ul>
 *   <li>Holds a {@link Mob} directly, not the broader {@code LivingEntity} -- {@code LivingEntity}
 *   includes {@code Player}/{@code ServerPlayer}, which {@code PlayerJig} already owns, and typing
 *   this scope against it would let the same object be scoped by two jig kinds with no type-level
 *   guard against it.</li>
 *   <li>No separate {@code MobResolver} class -- {@link #resolveUUID} and {@link #resolveScope}
 *   live directly on this class instead.</li>
 * </ul>
 */
public final class MobScope extends ASatchelScope implements PersistStitch {

    private final Mob mob;

    public MobScope(Mob mob) {
        // resolveUUID(Object) is the one real UUID-derivation implementation -- MobScope keeps
        // no copy of its own, same discipline PlayerScope follows for PlayerResolver.determineUUID.
        // Named differently from the deprecated instance override below: a static and an
        // instance method can't share a name+signature in the same class, and ASatchelScope
        // fixes the override's name to determineUUID.
        super(resolveUUID(mob));
        this.mob = Objects.requireNonNull(mob, "mob");
    }

    public Mob mob() {
        return mob;
    }

    @Override
    public String debugName() {
        return this.getClass().getSimpleName()
                + "[" + mob.getType() + "]"
                + uuid()
                + "@" + System.identityHashCode(this);
    }

    @Deprecated
    @Override
    protected UUID determineUUID(Object source) {
        return MobScope.resolveUUID(source);
    }

    /**
     * Deterministic UUID for a mob scope: the mob's own persistent UUID, direct -- no
     * world-identity-token fold (unlike {@code LevelScope.determineUUID}), no readiness-gated
     * defer window (unlike {@code LevelResolver}, matching {@code PlayerResolver}'s simpler shape
     * instead). An entity's own persistent UUID is always immediately resolvable on either side,
     * once something is actually looking for it.
     *
     * @throws IllegalStateException if {@code source} isn't a {@link Mob} -- mirrors
     *         {@code PlayerResolver.determineUUID}'s own contract (a real programming error, not
     *         a timing window, so it throws rather than returning null).
     */
    public static UUID resolveUUID(Object source) {
        Objects.requireNonNull(source, "source");
        if (!(source instanceof Mob mob)) {
            throw new IllegalStateException(
                    "MobJig source must be a Mob, got: " + source.getClass().getName()
            );
        }
        return mob.getUUID();
    }

    /**
     * Resolves a {@link MobScope} from a raw source object. No separate {@code MobResolver} class
     * exists for this jig kind (see class docs) -- this static plus {@link #resolveUUID} are
     * MobJig's whole binding surface, wired directly into {@code MobJigConfig}'s
     * {@code scopeResolver}/{@code uuidDeterminer} fields.
     */
    public static MobScope resolveScope(Object source) {
        Objects.requireNonNull(source, "source");
        if (!(source instanceof Mob mob)) {
            return null;
        }
        return new MobScope(mob);
    }

    @Override
    public Optional<CompoundTag> loadBundleTag(BundleKey<?> key) {
        return Optional.empty();
    }

    /**
     * The sanctioned entry point for tagging a live {@link Mob} into MobJig's scope machinery
     * outside of the poll's own reconciliation cycle -- see the MobScope.getFor() Contract wiki
     * page for the full boundary contract this method must keep. In order:
     * <ol>
     *   <li>Check {@code Satchel.isReady()} for the calling side -- cheap and side-agnostic.</li>
     *   <li>Reject an already-{@code isRemoved()} reference -- {@code Optional.empty()}, not a
     *   throw: the "bad reference" case.</li>
     *   <li>Call {@code Satchel.require().introduceSource(mob)} -- idempotent via
     *   {@code JigInfo.hasScope}'s existing guard; deliberately no second duplicate-check here.</li>
     *   <li>Return {@code Optional.of(new MobScope(mob))} -- immediate attachment, no poll wait.</li>
     * </ol>
     * Neither {@code Optional.empty()} case is an error: a caller that gets one back has nothing
     * further to retry synchronously -- a mob that becomes resolvable later is the poll's job to
     * pick up, not this call's. The returned {@code MobScope} is not a handle safe to hold across
     * ticks; once a mob's chunk becomes unresolvable, the poll tears the scope down.
     */
    public static Optional<MobScope> getFor(Mob mob) {
        Objects.requireNonNull(mob, "mob");

        if (!Satchel.isReady()) {
            return Optional.empty();
        }

        if (mob.isRemoved()) {
            return Optional.empty();
        }

        Satchel.require().introduceSource(mob);

        return Optional.of(new MobScope(mob));
    }
}
