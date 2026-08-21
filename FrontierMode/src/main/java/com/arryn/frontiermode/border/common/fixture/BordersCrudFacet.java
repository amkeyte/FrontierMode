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

        Optional<String> failure = failureReason(proposal);
        if (failure.isEmpty()) {
            return fixture.accept(proposal);
        }

        // Was a fixed generic message ("...see server log for the specific reason") -- the real
        // reason was only ever reaching OUT.warn(), never the exception itself, so the player-
        // facing rejection (BorderCommandHandler catches this and relays e.getMessage()) was
        // uninformative even after RM_FRO_011's original fix stopped it from looking like a
        // crash. Confirmed from a real /border add ~ ~ ~ 999999999 0 retest: player only saw the
        // generic text, not "radius 999999999 outside allowed range [1, 512]." Carrying the real
        // reason through here fixes that at the source instead of duplicating the reason-building
        // logic at every catch site.
        throw new IllegalStateException(failure.get());
    }

    /**
     * RM_FRO_011 originally added a hardcoded-no-op fix here for two things: neither
     * {@link BorderConstants#MIN_RADIUS}/{@link BorderConstants#MAX_RADIUS} nor layer collisions
     * were enforced on this path, even though {@code /border add}/{@code /border transform} both
     * route through it. Radius bounds are still enforced below. <b>Layer-collision rejection was
     * removed, RM_FRO_015, 2026-08-20</b> -- project owner's direct design call: Layer and Path are
     * definitionally unrelated (see Border Vocabulary's own "Layer" section, "only coincidentally
     * tied to Path"), and {@code DefaultBorderRules.getRelevant()} already fully resolves a
     * same-layer overlap by nearest center (see that method, and Border Vocabulary's Relevance
     * section: "lowest Layer wins, tie-broken by nearest center") -- a tie-break that was already
     * shipped and already correct *before* this uniqueness guard ever existed. The guard was
     * solving a problem {@code getRelevant()} didn't have: two borders sharing a layer, even two
     * that geometrically overlap, resolve deterministically without it. Global layer uniqueness was
     * actively harmful in practice: {@code BorderLogic.getInitial()}/{@code grow()}'s hardcoded
     * {@code layer = 0} / {@code previous.layer() + 1} would collide with any unrelated off-path
     * border already holding that value, blocking ordinary path growth for a reason that was never
     * a real correctness requirement (found via real playtest, RM_FRO_015).
     */
    public boolean validateProposal(BorderProposal proposal) {
        return failureReason(proposal).isEmpty();
    }

    /**
     * Real validation logic, shared by {@link #validateProposal} (kept boolean, still used
     * directly by {@code BorderLogic}'s organic-growth path) and {@link #applyProposal} (which
     * needs the actual reason text, not just pass/fail, to give the player a useful rejection
     * message instead of a generic one -- see that method's own comment). Layer values are
     * deliberately not checked for uniqueness here -- see {@link #validateProposal}'s own doc.
     */
    private Optional<String> failureReason(BorderProposal proposal) {
        fixture.requireServerSide();

        int radius = proposal.radius();
        if (radius < BorderConstants.MIN_RADIUS || radius > BorderConstants.MAX_RADIUS) {
            String reason = "radius " + radius + " outside allowed range ["
                    + BorderConstants.MIN_RADIUS + ", " + BorderConstants.MAX_RADIUS + "].";
            OUT.warn("[Border] Rejected proposal: " + reason);
            return Optional.of(reason);
        }

        int layerIndex = proposal.layerIndex();
        if (layerIndex < 0) {
            String reason = "negative layer " + layerIndex + ".";
            OUT.warn("[Border] Rejected proposal: " + reason);
            return Optional.of(reason);
        }

        return Optional.empty();
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