package com.arryn.frontiermode.border.common;

/**
 * Pure, Minecraft-type-free XZ geometry backing {@link BorderMath#distanceTo(net.minecraft.core.BlockPos, net.minecraft.core.BlockPos)}
 * and {@link BorderMath#direction(net.minecraft.core.BlockPos, net.minecraft.core.BlockPos)} --
 * split out purely so it can be unit-tested directly, the same reason Satchel's
 * {@code MobReconcileLogic} exists alongside {@code MobJig}: this project's test sourceSet has no
 * Minecraft userdev classes on its classpath (see {@code MobReconcileLogicTest}/
 * {@code TickThrottleTest}'s own "plain-JUnit, no mocking" precedent), so the
 * correctness-sensitive math has to live somewhere that doesn't import {@code BlockPos} at all.
 * {@link BorderMath}'s own public methods are thin {@code BlockPos}-unwrapping wrappers over this
 * class -- this class is never meant to be called directly outside {@link BorderMath}.
 */
public final class BorderMathLogic {

    private BorderMathLogic() {}

    /**
     * Straight-line XZ distance between {@code (x1,z1)} and {@code (x2,z2)}.
     */
    public static double distanceTo(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Normalized XZ direction from {@code (x1,z1)} to {@code (x2,z2)}, returned as
     * {@code {dx, dz}}. {@code {0, 0}} when the two points coincide -- there is no direction
     * between a point and itself.
     */
    public static double[] direction(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dz * dz);

        if (len == 0.0) {
            return new double[] {0.0, 0.0};
        }

        return new double[] {dx / len, dz / len};
    }
}
