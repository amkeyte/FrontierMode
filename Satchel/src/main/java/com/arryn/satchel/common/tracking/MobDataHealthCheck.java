package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.SatchelMod;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.mob.MobJig;
import com.arryn.satchel.common.jig.mob.MobScope;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.MobData;
import com.arryn.satchel.common.util.MobDataStore;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code MobData.has} (SAT_130) -- see {@link SatchelHealth}. It must answer exactly {@code
 * !MobData.read(...).isEmpty()} without the copy, so the server probe runs the same questions through
 * both on a synthetic mob that never joins the level (its data container is attached as it is
 * constructed) and checks they agree:
 * <ul>
 *   <li><b>absent</b> -- false before anything is written, and false for another namespace beside one
 *   that was;</li>
 *   <li><b>written</b> -- true after a write, and false after a write of an empty compound;</li>
 *   <li><b>saved data</b> -- false for an empty compound under the namespace, which a saved mob can
 *   carry, and {@code writeIfAbsent} then writes;</li>
 *   <li><b>removed</b> -- false once the mob is discarded and its container is gone;</li>
 *   <li><b>Satchel's own namespace</b> -- refused, as {@code read} refuses it.</li>
 * </ul>
 * A client mob has no container, so the client probe asks the canary's mob on that side and expects
 * false. One line per side.
 */
final class MobDataHealthCheck {

    private MobDataHealthCheck() {}

    private static final String MODULE = "satchel_health";

    /** The namespace the probe writes: not Satchel's own, which {@code MobData} refuses. */
    private static final ResourceLocation NAMESPACE = new ResourceLocation("satchelhealth", "mobdata_has");

    /** A second namespace the probe never writes. */
    private static final ResourceLocation OTHER = new ResourceLocation("satchelhealth", "mobdata_has_other");

    /** Ticks the server probe waits after the Overworld scope is ticking, so the world is settled. */
    private static final int RUN_AFTER_TICKS = 40;

    static void register() {
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.SERVER, Order.LATE,
                ServerProbe.class, ServerProbe::new);
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.CLIENT, Order.LATE,
                ClientProbe.class, ClientProbe::new);
    }

    private static void violation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: MobData.has " + message);
    }

    /** {@code has} must be {@code expected}, and agree with {@code read}. */
    private static void expect(Mob mob, boolean expected, String when) {
        boolean has = MobData.has(mob, NAMESPACE);
        if (has != expected) {
            violation("answered " + has + " " + when + ", expected " + expected);
        }
        boolean read = !MobData.read(mob, NAMESPACE).isEmpty();
        if (has != read) {
            violation("answered " + has + " " + when + " but read says " + (read ? "there is" : "there is not")
                    + " data");
        }
    }

    // =================================================================
    // Server
    // =================================================================

    /**
     * On the server's level host, in the Overworld: runs the cases once per world.
     */
    public static final class ServerProbe implements Ticking {

        private int ticks;

        @Override
        public void onTickAfter(ScopeInfo info) {
            if (!(info.level() instanceof ServerLevel level)) return;
            if (!level.dimension().equals(Level.OVERWORLD)) return;
            if (++ticks < RUN_AFTER_TICKS) return;
            if (!SatchelHealth.firstTime("mobdata:run")) return;

            run(level);
        }

        private static void run(ServerLevel level) {
            Mob mob = MobHealthCheck.createSyntheticMob(level);
            if (mob == null) {
                OUT.warn("[SatchelHealth] MobData.has probe: could not create a synthetic mob, so it was not run");
                return;
            }
            CompoundTag data = new CompoundTag();
            data.putInt("n", 1);

            // ---- absent, written, and a namespace never written ----
            expect(mob, false, "before anything was written");
            MobData.write(mob, NAMESPACE, data);
            expect(mob, true, "after a write");
            if (MobData.has(mob, OTHER)) {
                violation("answered true for a namespace nothing was written to, beside one that was");
            }

            // ---- an empty compound, written and as saved data carries it ----
            MobData.write(mob, NAMESPACE, new CompoundTag());
            expect(mob, false, "after a write of an empty compound");

            MobData.write(mob, NAMESPACE, data);
            CompoundTag saved = new CompoundTag();
            mob.saveWithoutId(saved);
            CompoundTag container = saved.getCompound("ForgeCaps").getCompound(MobDataStore.ID.toString());
            if (!container.contains(NAMESPACE.toString(), Tag.TAG_COMPOUND)) {
                violation("could not stage the saved-data case: the mob's save does not carry what was written");
            } else {
                container.put(NAMESPACE.toString(), new CompoundTag());
                mob.load(saved);
                expect(mob, false, "for an empty compound loaded under the namespace");
                if (!MobData.writeIfAbsent(mob, NAMESPACE, data)) {
                    violation("left writeIfAbsent refusing a namespace that holds nothing");
                }
                expect(mob, true, "after writeIfAbsent over an empty compound");
            }

            // ---- Satchel's own namespace ----
            try {
                MobData.has(mob, new ResourceLocation(SatchelMod.MODID, "probe"));
                violation("accepted Satchel's own namespace, which read refuses");
            } catch (IllegalArgumentException refused) {
                // as read does
            }

            // ---- a removed mob ----
            mob.discard();
            expect(mob, false, "once the mob was removed");

            OUT.info("[SatchelHealth] MobData.has probe: on the server it was false before a write, true after, "
                    + "false for another namespace, after an empty write and for an empty compound loaded under "
                    + "the namespace, and false once the mob was removed; it agreed with read throughout");
        }
    }

    // =================================================================
    // Client
    // =================================================================

    /**
     * On the client's level host: once the canary has a scope on this side, asks its mob. A client mob
     * has no container, so the answer is false, and it does not throw.
     */
    public static final class ClientProbe implements Ticking {

        private boolean done;

        @Override
        public void onTickAfter(ScopeInfo info) {
            if (done) return;

            Optional<UUID> canary = SatchelHealth.canaryUuid();
            if (canary.isEmpty()) return;
            Optional<MobJig> jig = CanaryHealthCheck.mobJig(info);
            if (jig.isEmpty()) return;
            Optional<MobScope> scope = jig.get().scopeFor(canary.get());
            if (scope.isEmpty()) return;

            done = true;
            if (!SatchelHealth.firstTime("mobdata:client")) return;

            Mob mob = scope.get().mob();
            if (!mob.level().isClientSide()) {
                violation("client probe was given a mob of a server level: " + mob);
                return;
            }
            expect(mob, false, "for the canary's mob on the client, which has no container");

            OUT.info("[SatchelHealth] MobData.has probe: on the client it was false for the canary's mob, which "
                    + "has no container, and agreed with read");
        }
    }
}
