package com.arryn.satchel.common.tracking;

import com.arryn.satchel.common.bundle.Capability;
import com.arryn.satchel.common.bundle.SatchelBundle;
import com.arryn.satchel.common.fixture.SatchelFixture;
import com.arryn.satchel.common.identity.BundleKey;
import com.arryn.satchel.common.identity.FixtureKey;
import com.arryn.satchel.common.jig.config.JigBundles;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ForgeEgress;
import com.arryn.satchel.common.jig.guts.SatchelScope;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.jig.mob.MobJig;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraftforge.fml.LogicalSide;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The canary's wiring (SAT_125) -- see {@link MobHealthCheck} for the canary itself.
 *
 * <p>
 * A client cannot learn which mob to care about by itself: interest is pushed per side by the
 * module, and a mob jig has no scope, so no Trinket, until interest exists. So the server writes the
 * canary's UUID into a {@link CanaryFixture}, in its own {@link CanaryBundle} on the health level jig
 * (synced, never saved), and a {@link CanaryTrinket} on that jig, which exists on both sides,
 * mirrors it into its own side's mob jig with {@code interest}, gated by {@code isReady(info)} until
 * the parcel has arrived. When the value changes or clears it calls {@code lostInterest} for the one
 * it registered. Both sides resolving the mob through {@code ForgeEgress} is what proves the BOTH
 * path of the mob jig.
 */
final class CanaryHealthCheck {

    private CanaryHealthCheck() {}

    /** How long the canary is scoped before the unload probe releases it. */
    private static final int UNLOAD_PROBE_AFTER_TICKS = 40;

    static final BundleKey<CanaryBundle> CANARY_BUNDLE =
            new BundleKey<>("satcheltracker:canary_bundle", CanaryBundle.class);

    static final FixtureKey<CanaryFixture> CANARY_FIXTURE =
            new FixtureKey<>("satcheltracker:canary_fixture", CanaryFixture.class);

    static JigBundles.BundleDecl<LevelScope, CanaryBundle> bundleDecl() {
        var fixture = new JigBundles.FixtureDecl<CanaryFixture>(
                CANARY_FIXTURE, CanaryFixture::new, JigPolicies.CreatePolicy.ALWAYS);
        return new JigBundles.BundleDecl<LevelScope, CanaryBundle>(
                CANARY_BUNDLE,
                (LevelScope scope) -> new CanaryBundle(scope, CANARY_BUNDLE),
                List.of(fixture));
    }

    /**
     * Networking and never persistence: the canary's UUID reaches clients and no stale one survives
     * a restart. Capabilities are declared per bundle, which is why this is not a field of the
     * tracker bundle.
     */
    public static final class CanaryBundle extends SatchelBundle {

        public CanaryBundle(SatchelScope scope, BundleKey<CanaryBundle> key) {
            super(scope, key);
        }

        @Override
        public int required() {
            return Capability.NETWORKING.bit();
        }
    }

    /**
     * The canary's UUID, or none. A cleared value is written as the nil UUID, because an absent
     * field is not sent and a client would keep the last value it had.
     */
    public static final class CanaryFixture extends SatchelFixture {

        private static final UUID NONE = new UUID(0L, 0L);

        private UUID canary;

        public CanaryFixture() {
            registerUUID("canary", () -> canary == null ? NONE : canary,
                    value -> canary = NONE.equals(value) ? null : value);
        }

        /** The canary's UUID, or null for none. */
        public UUID canary() {
            return canary;
        }

        public void setCanary(UUID uuid) {
            if (Objects.equals(canary, uuid)) return;
            canary = uuid;
            markDirty();
        }
    }

    private static Optional<CanaryFixture> fixtureOf(ScopeInfo info) {
        return info.ask(CANARY_BUNDLE).flatMap(b -> b.get(CANARY_FIXTURE));
    }

    /** This side's mob jig for the health checks, without needing a bound thread. */
    static Optional<MobJig> mobJig(ScopeInfo info) {
        return ScopeInfoImpl.of(info).jigInfo().foundation().askJigInfo(MobHealthCheck.MOB_JIG)
                .map(ji -> (MobJig) ji.jig);
    }

    /**
     * On the health level jig, on both sides. On the server it keeps a canary alive and writes its UUID
     * to the fixture; on both it mirrors the fixture into the mob jig's interest.
     */
    public static final class CanaryTrinket implements Ticking {

        /** The UUID this side registered interest in, so it can drop it when the value changes. */
        private UUID mirrored;

        @Override
        public void onAttach(ScopeInfo info) {
            // Trinkets attach before the module's Loaded handler creates bundles, so ask() would be
            // empty here; getOrCreate makes the bundle now. On a client it then waits for its parcel.
            info.getOrCreate(CANARY_BUNDLE);
        }

        /** Until the synced value has arrived, the mirror has nothing to act on. */
        @Override
        public boolean isReady(ScopeInfo info) {
            return fixtureOf(info).map(SatchelFixture::isReady).orElse(false);
        }

        @Override
        public void onTickAfter(ScopeInfo info) {
            ScopeInfoImpl impl = ScopeInfoImpl.of(info);
            if (impl.jigInfo().side() == LogicalSide.SERVER) {
                maintainCanary(impl);
                InterestHealthCheck.drive(impl);
            }
            mirror(impl);
            driveUnload(impl);
        }

        /** Ticks this Trinket has seen the canary scoped, before the unload probe runs. */
        private int scopedTicks;

        /**
         * SAT_135: once per side per world, a little after the canary has a scope, releases it. The
         * canary's own {@code Leaving} and detach then check that no second scope is built from inside
         * the unload ({@link MobHealthCheck.CanaryMobTrinket}), and here, once the release has returned,
         * that the scope is built again exactly once, since the mob is still in the level and still of
         * interest. A client whose canary has left tracking range has no scope to release and is left
         * alone.
         */
        private void driveUnload(ScopeInfoImpl info) {
            UUID canary = mirrored;
            if (canary == null) {
                canary = SatchelHealth.canaryUuid().orElse(null);
            }
            if (canary == null) return;

            Optional<MobJig> jigFound = mobJig(info);
            if (jigFound.isEmpty()) return;
            MobJig jig = jigFound.get();

            if (jig.scopeFor(canary).isEmpty()) {
                scopedTicks = 0;
                return;
            }
            if (++scopedTicks < UNLOAD_PROBE_AFTER_TICKS) return;

            LogicalSide side = info.jigInfo().side();
            if (!SatchelHealth.firstTime("mobunload:released:" + side)) return;

            jig.release(canary);

            if (jig.scopeFor(canary).isEmpty()) {
                OUT.info("[SatchelHealth] Mob unload probe: the canary was released on the " + side
                        + " side and its mob left the level before a scope could be built again");
                return;
            }
            // Its attach recorded the new scope; a second attach before the first detached would have
            // been refused there.
            if (!SatchelHealth.state().canaryScopes(side).contains(canary)) {
                SatchelHealth.violation("[SatchelHealth] VIOLATION: Mob unload: the canary's scope was built again "
                        + "on the " + side + " side but its attach was not recorded");
            }
            OUT.info("[SatchelHealth] Mob unload probe: the canary was released on the " + side
                    + " side and its scope was built again once");
        }

        @Override
        public void onDetach(ScopeInfo info) {
            // This level scope is going: the interest it registered goes with it.
            if (mirrored != null) {
                UUID gone = mirrored;
                mirrored = null;
                mobJig(info).ifPresent(jig -> jig.lostInterest(gone));
            }
        }

        @Override
        public String describe() {
            return "mirrored=" + (mirrored == null ? "none" : Ids.shortId(mirrored));
        }

        /**
         * Server only: ensures a live canary exists under the mob jig, so its CLIENT/BOTH path has a
         * guaranteed-present subject to exercise continuously rather than depending on real gameplay.
         * A Trinket on this jig, not the mob jig's, because the mob jig has zero scopes whenever no
         * canary exists and so can never wake itself to spawn one. Pinned to the Overworld, the only
         * dimension guaranteed a keep-loaded spawn region with nobody in it; checked before the
         * throttle so another dimension's tick does not consume the window.
         */
        private void maintainCanary(ScopeInfoImpl info) {
            if (!(info.scope() instanceof LevelScope levelScope)) return;
            if (!(levelScope.level() instanceof ServerLevel serverLevel)) return;
            if (!serverLevel.dimension().equals(Level.OVERWORLD)) return;

            HealthState state = SatchelHealth.state();
            if (!state.canaryCheck.allow()) return;

            // A canary is persistent and the world keeps it, so every earlier run's canary is still at
            // spawn carrying the mark, and this run's UUID is not remembered across a restart. Repeated on
            // every pass, because a stray's chunk can finish loading a few ticks after the first.
            int strays = discardStrays(serverLevel, state.canaryUuid);
            if (strays > 0) {
                OUT.info("[SatchelHealth] Canary cleanup: discarded " + strays + " stale canary(ies) left by an "
                        + "earlier run");
            }

            ForgeEgress egress = info.jigInfo().foundation().egress();
            UUID existing = state.canaryUuid;
            if (existing != null && egress.getEntity(serverLevel, existing).isPresent()) return;

            Optional<MobJig> jigFound = mobJig(info);
            if (jigFound.isEmpty()) return;
            MobJig jig = jigFound.get();

            // The previous canary is gone (killed, or its chunk never came back): stop caring.
            if (existing != null) jig.lostInterest(existing);

            Mob canary = MobHealthCheck.spawnSyntheticMob(serverLevel);
            if (canary == null) return;
            CanaryData.mark(canary);

            UUID uuid = canary.getUUID();
            state.canaryUuid = uuid;
            mirrored = uuid;

            // Interest is explicit, and the scope is built in this call because the mob is in the level.
            jig.interest(uuid);
            if (jig.scopeFor(uuid).isEmpty()) {
                SatchelHealth.violation("[SatchelHealth] VIOLATION: Canary scopeFor(" + Ids.shortId(uuid)
                        + ") was empty right after interest() for a mob that is in the level");
            }

            fixtureOf(info).ifPresent(f -> f.setCanary(uuid));

            OUT.info("[SatchelHealth] Canary spawned: " + canary.getType()
                    + " uuid=" + Ids.shortId(uuid)
                    + " dim=" + serverLevel.dimension().location());
        }

        /**
         * Discards every loaded canary in {@code level} except {@code keep} (null on a fresh boot, when
         * every marked mob is a stray).
         *
         * @return how many it discarded
         */
        private static int discardStrays(ServerLevel level, UUID keep) {
            List<? extends Mob> strays = level.getEntities(EntityTypeTest.forClass(Mob.class),
                    mob -> CanaryData.isCanary(mob) && !mob.getUUID().equals(keep));
            for (Mob stray : strays) stray.discard();
            return strays.size();
        }

        /**
         * Both sides: makes this side's mob-jig interest match the fixture.
         */
        private void mirror(ScopeInfoImpl info) {
            Optional<CanaryFixture> fixture = fixtureOf(info);
            if (fixture.isEmpty() || !fixture.get().isReady()) return;

            UUID wanted = fixture.get().canary();
            if (Objects.equals(wanted, mirrored)) return;

            Optional<MobJig> jigFound = mobJig(info);
            if (jigFound.isEmpty()) return;
            MobJig jig = jigFound.get();

            if (mirrored != null) jig.lostInterest(mirrored);
            mirrored = wanted;

            if (wanted != null) {
                SatchelHealth.state().canaryUuid = wanted;
                jig.interest(wanted);
                OUT.info("[SatchelHealth] Canary interest mirrored on the "
                        + info.jigInfo().side() + " side: uuid=" + Ids.shortId(wanted));
            }
        }
    }
}
