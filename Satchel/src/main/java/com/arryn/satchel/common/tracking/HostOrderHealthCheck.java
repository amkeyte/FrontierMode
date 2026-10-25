package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.ingress.Scopes;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.host.HostJigs;
import com.arryn.satchel.common.trinket.Order;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Tick order across the two hosts (SAT_134 probe 2) -- see {@link SatchelHealth}. Four server-only
 * host Trinkets, two in each direction, because the foundation's one global list sorts by {@code
 * order} and only then by jig registration index, and the level host registers before the player host:
 * <ul>
 *   <li><b>level EARLY, then player NORMAL</b> -- the shape of Feral's presence and player tick. The
 *   player Trinket looks the level Trinket up by type in the same tick and sees that tick's stamp. This
 *   leg agrees with registration order, so on its own it could not tell a working {@code order} from
 *   one that was ignored;</li>
 *   <li><b>player EARLY, then level NORMAL</b> -- the reverse. The Trinket that must run first sits on
 *   the jig registered second, so it runs first only if {@code order} decides.</li>
 * </ul>
 * Each writer stamps the foundation's tick number in each phase; each reader compares it with the
 * current tick in the same phase. All four are {@link HostProbe}s, so they also confirm their own life.
 */
final class HostOrderHealthCheck {

    private HostOrderHealthCheck() {}

    private static final String MODULE = "satchel_health";

    static void register() {
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.SERVER, Order.EARLY,
                LevelWriter.class, LevelWriter::new);
        Satchel.contributePlayerTrinket(MODULE, JigPolicies.SideApplicability.SERVER, Order.NORMAL,
                PlayerReader.class, PlayerReader::new);
        Satchel.contributePlayerTrinket(MODULE, JigPolicies.SideApplicability.SERVER, Order.EARLY,
                PlayerWriter.class, PlayerWriter::new);
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.SERVER, Order.NORMAL,
                LevelReader.class, LevelReader::new);
    }

    private static long tick(ScopeInfo info) {
        return info.jigInfo().trinketTick().tickNumber();
    }

    /** A probe that records the tick it last ran in, in each phase. */
    abstract static class Writer extends HostProbe {

        private volatile long stampedBefore;
        private volatile long stampedAfter;

        Writer(String name) {
            super(name, JigPolicies.SideApplicability.SERVER);
        }

        @Override
        public void onTickBefore(ScopeInfo info) {
            super.onTickBefore(info);
            stampedBefore = tick(info);
        }

        @Override
        public void onTickAfter(ScopeInfo info) {
            super.onTickAfter(info);
            stampedAfter = tick(info);
        }

        long stampedBefore() {
            return stampedBefore;
        }

        long stampedAfter() {
            return stampedAfter;
        }
    }

    /** A probe that checks a writer on the other host ran first in the same tick, in each phase. */
    abstract static class Reader extends HostProbe {

        private final String writerName;

        Reader(String name, String writerName) {
            super(name, JigPolicies.SideApplicability.SERVER);
            this.writerName = writerName;
        }

        @Override
        public void onTickBefore(ScopeInfo info) {
            super.onTickBefore(info);
            forEachWriter(info, writer -> expect(info, "before", writer, writer.stampedBefore()));
        }

        @Override
        public void onTickAfter(ScopeInfo info) {
            super.onTickAfter(info);
            forEachWriter(info, writer -> expect(info, "after", writer, writer.stampedAfter()));
        }

        /** Hands {@code check} every writer this probe is meant to see from {@code info}'s scope. */
        abstract void forEachWriter(ScopeInfo info, Consumer<Writer> check);

        private void expect(ScopeInfo info, String phase, Writer writer, long stamped) {
            long now = tick(info);

            // A writer attached in this very tick first runs in the next one, so it has no stamp yet.
            if (writer.attachedInTick() >= now) return;

            if (stamped != now) {
                violation(writerName + " had not run before " + probeName() + " in the " + phase + " step of tick "
                        + now + " (its last stamp was tick " + stamped + ") on " + info.debugName());
            }
            SatchelHealth.logOnce("order:" + probeName() + ":" + side(), "[SatchelHealth] Host order probe: the "
                    + writerName + " ran before the " + probeName() + " in the same tick, in both phases, on the "
                    + side() + " side");
        }
    }

    // =================================================================
    // Level EARLY, then player NORMAL
    // =================================================================

    public static final class LevelWriter extends Writer {

        public LevelWriter() {
            super("early level probe");
        }
    }

    /** On the player host at NORMAL: finds the EARLY level Trinket of the level its player is in. */
    public static final class PlayerReader extends Reader {

        public PlayerReader() {
            super("normal player probe", "early level probe");
        }

        @Override
        void forEachWriter(ScopeInfo info, Consumer<Writer> check) {
            Level level = info.level();
            Optional<ScopeInfo> levelScope = Scopes.of(HostJigs.LEVEL_HOST, level);
            if (levelScope.isEmpty()) return;
            Satchel.trinket(HostJigs.LEVEL_HOST, levelScope.get().scope(), LevelWriter.class).ifPresent(check);
        }
    }

    // =================================================================
    // Player EARLY, then level NORMAL
    // =================================================================

    public static final class PlayerWriter extends Writer {

        public PlayerWriter() {
            super("early player probe");
        }
    }

    /**
     * On the level host at NORMAL: finds the EARLY player Trinket of each player in its level. A level
     * with no player has nothing to check.
     */
    public static final class LevelReader extends Reader {

        public LevelReader() {
            super("normal level probe", "early player probe");
        }

        @Override
        void forEachWriter(ScopeInfo info, Consumer<Writer> check) {
            if (!(info.level() instanceof ServerLevel level)) return;
            for (ServerPlayer player : level.players()) {
                Optional<ScopeInfo> playerScope = Scopes.of(HostJigs.PLAYER_HOST, player);
                if (playerScope.isEmpty()) continue;
                Satchel.trinket(HostJigs.PLAYER_HOST, playerScope.get().scope(), PlayerWriter.class)
                        .ifPresent(check);
            }
        }
    }
}
