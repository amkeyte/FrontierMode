package com.arryn.satchel.common.trinket;

import com.arryn.satchel.common.jig.config.JigPolicies;
import net.minecraftforge.fml.LogicalSide;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A jig config's declaration of one Trinket: its type, which is also its lookup key, the factory
 * Satchel calls once per scope when the scope loads, and its {@code order} in the global tick (see
 * {@link Order}).
 *
 * <p>
 * Two more fields matter only on a host jig, where modules contribute Trinkets: the {@code side} the
 * Trinket exists on (a declaration on an ordinary jig applies wherever the jig does) and the {@code
 * owner}, the id of the contributing module, which {@code /satchel trinkets} prints.
 */
public record TrinketDecl<T extends Trinket>(
        Class<T> type,
        Supplier<? extends T> factory,
        int order,
        JigPolicies.SideApplicability side,
        String owner
) {

    public TrinketDecl {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(side, "side");
    }

    public TrinketDecl(Class<T> type, Supplier<? extends T> factory) {
        this(type, factory, Order.NORMAL);
    }

    public TrinketDecl(Class<T> type, Supplier<? extends T> factory, int order) {
        this(type, factory, order, JigPolicies.SideApplicability.BOTH, null);
    }

    public TrinketDecl<T> withOrder(int order) {
        return new TrinketDecl<>(type, factory, order, side, owner);
    }

    /**
     * Whether a scope on {@code logicalSide} gets this Trinket.
     *
     * @deprecated Discouraged, and probably going to be removed: it takes Forge's {@code LogicalSide}, and a
     *             module has no need to ask. A Trinket's side is declared with {@code SideApplicability} when it is
     *             contributed, and Satchel applies it.
     */
    @Deprecated
    public boolean appliesTo(LogicalSide logicalSide) {
        return switch (side) {
            case BOTH -> true;
            case CLIENT -> logicalSide == LogicalSide.CLIENT;
            case SERVER -> logicalSide == LogicalSide.SERVER;
        };
    }
}
