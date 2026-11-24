package com.arryn.satchel.common.tracking;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.jig.config.JigPolicies;
import com.arryn.satchel.common.jig.guts.LogicalSide;
import com.arryn.satchel.common.jig.guts.ScopeInfo;
import com.arryn.satchel.common.jig.guts.ScopeInfoImpl;
import com.arryn.satchel.common.trinket.Order;
import com.arryn.satchel.common.trinket.Ticking;
import com.arryn.satchel.common.util.Ids;
import com.arryn.satchel.common.util.out.OUT;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code StackData}'s enchantment calls by id (SAT_199) -- see {@link SatchelHealth}. {@code StackData} is a line
 * class, and a unit test cannot boot the registries its lookup reads, so the probe runs the calls in a real game, on a
 * stack of its own, and checks what each leaves:
 * <ul>
 *   <li><b>a fresh stack</b> has no enchantments and reads 0 for each id;</li>
 *   <li><b>enchanting by id</b> reads back at the level given, sets and does not append (a second call replaces the
 *   level), and adds a second enchantment beside the first;</li>
 *   <li><b>replacing the set</b> by id leaves exactly that set, and an empty map clears it;</li>
 *   <li><b>an id with no enchantment behind it</b> reads as level 0 and is not in the set;</li>
 *   <li><b>a write with such an id</b> throws {@code IllegalArgumentException}, whether it is the whole call or the
 *   second entry of a set, and leaves the stack as it was.</li>
 * </ul>
 * Once per side, from the level host's tick, since both sides read and write stacks and each looks the ids up in its own
 * level's registries.
 *
 * <p>
 * The check is shared. A line implements {@link Stacks} over its {@code StackData} and registers the check with
 * {@link #register(Stacks)}; the expectations are here, so the two lines are held to the same ones. A call that throws
 * what the check did not expect is a violation and ends the probe's run, and does not stop the game.
 */
final class StackDataHealthCheck {

    private StackDataHealthCheck() {}

    private static final String MODULE = "satchel_health";

    /** Two enchantments every version has, by id, and an id that names none. */
    private static final ResourceLocation SHARPNESS = Ids.id("minecraft", "sharpness");
    private static final ResourceLocation UNBREAKING = Ids.id("minecraft", "unbreaking");
    private static final ResourceLocation UNKNOWN = Ids.id("satchel_health", "no_such_enchantment");

    /** Contributes the probe, which runs through {@code stacks}. Called by a line that has this probe. */
    static void register(Stacks<?> stacks) {
        Satchel.contributeLevelTrinket(MODULE, JigPolicies.SideApplicability.BOTH, Order.LATE,
                StackDataProbe.class, () -> new StackDataProbe(stacks));
    }

    private static void violation(String message) {
        SatchelHealth.violation("[SatchelHealth] VIOLATION: StackData by id " + message);
    }

    /**
     * The line's {@code StackData} on a stack of the line's own type {@code S}, which this check cannot name.
     *
     * @param <S> the line's item stack
     */
    interface Stacks<S> {

        /** A new stack of a plain item with no enchantments. */
        S newStack();

        /** {@code StackData.enchantmentLevel(stack, enchantment)}. */
        int level(S stack, ResourceLocation enchantment);

        /** {@code StackData.enchantmentsById(stack)}. */
        Map<ResourceLocation, Integer> all(S stack);

        /** {@code StackData.enchant(stack, level, enchantment, enchantLevel)}. */
        void enchant(S stack, Level level, ResourceLocation enchantment, int enchantLevel);

        /** {@code StackData.setEnchantmentsById(stack, level, enchantments)}. */
        void replace(S stack, Level level, Map<ResourceLocation, Integer> enchantments);
    }

    /** On the level host, both sides: runs the calls once, from its tick on its own side's thread. */
    public static final class StackDataProbe implements Ticking {

        private final Stacks<?> stacks;

        StackDataProbe(Stacks<?> stacks) {
            this.stacks = stacks;
        }

        @Override
        public void onTickAfter(ScopeInfo info) {
            ScopeInfoImpl impl = ScopeInfoImpl.of(info);
            LogicalSide side = impl.jigInfo().side();
            if (!SatchelHealth.firstTime("stackdata:" + side)) return;

            Run<?> run = new Run<>(stacks, impl.level(), side);
            try {
                run.fresh();
                run.enchantByIdSetsAndAdds();
                run.replaceAndClear();
                run.unknownId();
            } catch (RuntimeException e) {
                violation("on the " + side + " side threw " + e + " where the check expected none, which ends the probe");
                return;
            }

            if (run.violations == 0) {
                OUT.info("[SatchelHealth] StackData probe: on the " + side + " side enchanting by id read back, set "
                        + "and did not append, replacing the set and clearing it worked, and an id with no "
                        + "enchantment read as 0 and refused a write without touching the stack");
            }
        }
    }

    /** One run on one stack. */
    private static final class Run<S> {
        private final Stacks<S> stacks;
        private final Level level;
        private final LogicalSide side;
        private final S stack;
        int violations;

        @SuppressWarnings("unchecked")
        Run(Stacks<?> stacks, Level level, LogicalSide side) {
            this.stacks = (Stacks<S>) stacks;
            this.level = level;
            this.side = side;
            this.stack = this.stacks.newStack();
        }

        private void expect(boolean ok, String what) {
            if (ok) return;
            violations++;
            violation("on the " + side + " side: " + what);
        }

        private void expectSet(Map<ResourceLocation, Integer> expected, String after) {
            Map<ResourceLocation, Integer> actual = stacks.all(stack);
            expect(actual.equals(expected), "after " + after + " the stack's enchantments were " + actual
                    + ", expected " + expected);
        }

        void fresh() {
            expectSet(Map.of(), "a new stack");
            expect(stacks.level(stack, SHARPNESS) == 0, "a new stack read a level other than 0 for " + SHARPNESS);
        }

        void enchantByIdSetsAndAdds() {
            stacks.enchant(stack, level, SHARPNESS, 3);
            expect(stacks.level(stack, SHARPNESS) == 3,
                    "enchanting " + SHARPNESS + " at 3 read back " + stacks.level(stack, SHARPNESS));
            expectSet(Map.of(SHARPNESS, 3), "enchanting " + SHARPNESS + " at 3");

            stacks.enchant(stack, level, SHARPNESS, 5);
            expectSet(Map.of(SHARPNESS, 5), "enchanting " + SHARPNESS + " again at 5, which sets and does not append");

            stacks.enchant(stack, level, UNBREAKING, 2);
            expectSet(Map.of(SHARPNESS, 5, UNBREAKING, 2), "enchanting " + UNBREAKING + " beside " + SHARPNESS);
            expect(stacks.level(stack, UNBREAKING) == 2, "the level of " + UNBREAKING + " was not 2");
        }

        void replaceAndClear() {
            stacks.replace(stack, level, Map.of(UNBREAKING, 1));
            expectSet(Map.of(UNBREAKING, 1), "replacing the set with " + UNBREAKING);
            expect(stacks.level(stack, SHARPNESS) == 0, "the replaced-out " + SHARPNESS + " still read a level");

            stacks.replace(stack, level, Map.of());
            expectSet(Map.of(), "replacing the set with an empty map");
        }

        void unknownId() {
            stacks.replace(stack, level, Map.of(SHARPNESS, 4));
            Map<ResourceLocation, Integer> before = stacks.all(stack);

            expect(stacks.level(stack, UNKNOWN) == 0, "an id with no enchantment read a level other than 0");
            expect(!stacks.all(stack).containsKey(UNKNOWN), "an id with no enchantment is in the set");

            expect(refused(() -> stacks.enchant(stack, level, UNKNOWN, 1)),
                    "enchanting an id with no enchantment did not throw IllegalArgumentException");
            expectSet(before, "a refused enchant");

            // A valid entry first, so a stack changed before the failing entry is looked up would show.
            Map<ResourceLocation, Integer> mixed = new LinkedHashMap<>();
            mixed.put(UNBREAKING, 2);
            mixed.put(UNKNOWN, 1);
            expect(refused(() -> stacks.replace(stack, level, mixed)),
                    "replacing the set with an id with no enchantment among its entries did not throw "
                            + "IllegalArgumentException");
            expectSet(before, "a refused replace");
        }

        private static boolean refused(Runnable call) {
            try {
                call.run();
                return false;
            } catch (IllegalArgumentException expected) {
                return true;
            }
        }
    }
}
