package com.arryn.frontiermode.border.common.fixture;


import com.arryn.frontiermode.border.common.BorderConstants;
import com.arryn.satchel.common.util.out.OUT;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

///
/// provides the direct interface to interact with proposals.
public class BordersCrudFacet {
    private final BordersFixture fixture;

    BordersCrudFacet(BordersFixture fixture) {
        this.fixture = fixture;
    }

    //return a new proposal
    public BorderProposal getProposal() {
        return new BorderProposal(fixture.logic);
    }

    public Border applyProposal(BorderProposal proposal) {
        fixture.requireServerSide();
        proposal.requireNotConsumed();

        if (validateProposal(proposal)) {
            return fixture.accept(proposal);
        }
        throw new IllegalStateException(
                "Border proposal rejected by validation -- see server log for the specific reason."
        );
    }

    /**
     * RM_FRO_011: was a hardcoded no-op ("pretty much future use") -- neither
     * {@link BorderConstants#MIN_RADIUS}/{@link BorderConstants#MAX_RADIUS} nor layerIndex
     * collisions were enforced on this path, even though {@code /border add}/{@code /border
     * transform} both route through it (the organic growth path enforces radius bounds itself, in
     * {@code DefaultBorderRules.chooseNextRadius}, but that's a separate call site this method
     * doesn't gate). Concretely, this used to let {@code /border add ~ ~ ~ 999999999 0} create a
     * border with an unbounded radius and a {@code layerIndex} that collides with an existing
     * border's -- {@code layerIndex} is what {@code DefaultBorderRules.getRelevant()} sorts by for
     * oldest-ring-wins overlap resolution (see the Border wiki page and Progression's design), so
     * an uncontrolled collision silently corrupts that ordering.
     */
    public boolean validateProposal(BorderProposal proposal) {
        fixture.requireServerSide();

        int radius = proposal.radius();
        if (radius < BorderConstants.MIN_RADIUS || radius > BorderConstants.MAX_RADIUS) {
            OUT.warn(
                    "[Border] Rejected proposal: radius " + radius + " outside allowed range ["
                            + BorderConstants.MIN_RADIUS + ", " + BorderConstants.MAX_RADIUS + "]."
            );
            return false;
        }

        int layerIndex = proposal.layerIndex();
        if (layerIndex < 0) {
            OUT.warn("[Border] Rejected proposal: negative layerIndex " + layerIndex + ".");
            return false;
        }

        // A proposal updating an existing border (via insert(border)) keeps that border's own id,
        // so it's correctly excluded from colliding with itself here -- only a *different*
        // border already holding this layerIndex counts as a real collision.
        boolean collides = fixture.all().stream()
                .anyMatch(b -> !b.id().equals(proposal.id()) && b.layerIndex() == layerIndex);
        if (collides) {
            OUT.warn(
                    "[Border] Rejected proposal: layerIndex " + layerIndex
                            + " collides with an existing border's layerIndex."
            );
            return false;
        }

        return true;
    }

    public boolean remove(UUID uuid){
        fixture.requireServerSide();

        return fixture.remove(uuid);
    }

    public boolean remove(Border border){
        fixture.requireServerSide();

        return fixture.remove(border.id());
    }

    public Optional<Border> get(UUID uuid) {
        return fixture.get(uuid);
    }

    public List<Border> all() {
        return fixture.all();
    }
}