package com.arryn.satchel.server.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.level.LevelScope;
import com.arryn.satchel.common.platform.PlatformBound;
import com.arryn.satchel.common.tracking.CanaryData;
import com.arryn.satchel.common.tracking.SatchelHealth;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.out.OUT;
import com.arryn.satchel.common.util.schedule.SchedulerClock;
import com.arryn.satchel.common.util.schedule.TickThrottler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.List;
import java.util.UUID;

/**
 * Discards the canaries an earlier run left behind (SAT_113). A canary is persistent and the world keeps it,
 * so every earlier run's canary is still at spawn carrying the mark, and this run's UUID is not remembered
 * across a restart. Repeated on every pass, because a stray's chunk can finish loading a few ticks after the
 * first. Server only: it walks the loaded entities of a {@code ServerLevel}. The canary itself is kept alive by
 * {@code CanaryHealthCheck}, which is why this ran from there until it had to name the server's class.
 */
@PlatformBound(reason = "health-probe")
public final class CanaryCleanupHealthCheck {

    private CanaryCleanupHealthCheck() {}

    private static final String MODULE = "satchel_health";

    /** Ticks between passes. */
    private static final int EVERY_TICKS = 200;

    public static void register() {
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.SERVER, Order.LATE,
                Cleanup.class, Cleanup::new);
    }

    /**
     * On the server's level host, in the Overworld, the only dimension guaranteed a keep-loaded spawn region
     * with nobody in it: discards every loaded canary but the current one.
     */
    public static final class Cleanup implements Ticking {

        private final TickThrottler pass = new TickThrottler(EVERY_TICKS, new SchedulerClock.AutoClock());

        @Override
        public void onTickAfter(ScopeInfo info) {
            if (!(info.scope() instanceof LevelScope levelScope)) return;
            if (!(levelScope.level() instanceof ServerLevel level)) return;
            if (!level.dimension().equals(Level.OVERWORLD)) return;
            if (!pass.allow()) return;

            int strays = discardStrays(level, SatchelHealth.canaryUuid().orElse(null));
            if (strays > 0) {
                OUT.info("[SatchelHealth] Canary cleanup: discarded " + strays + " stale canary(ies) left by an "
                        + "earlier run");
            }
        }

        /**
         * Discards every loaded canary in {@code level} except {@code keep} (null on a fresh boot, when every
         * marked mob is a stray).
         *
         * @return how many it discarded
         */
        private static int discardStrays(ServerLevel level, UUID keep) {
            List<? extends Mob> strays = level.getEntities(EntityTypeTest.forClass(Mob.class),
                    mob -> CanaryData.isCanary(mob) && !mob.getUUID().equals(keep));
            for (Mob stray : strays) stray.discard();
            return strays.size();
        }
    }
}
